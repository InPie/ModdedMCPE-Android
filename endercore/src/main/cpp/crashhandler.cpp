#include <unwind.h>
#include <iomanip>
#include <sstream>
#include <cxxabi.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <signal.h>
#include <ucontext.h>
#include <cstring>
#include <string>

#define LOG_TAG "CrashHandler-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern JavaVM *g_jvm;
extern jclass g_crashHandlerClass;
extern jmethodID g_onNativeCrashMethod;

// ======== start crash handler ========
static volatile sig_atomic_t g_handlingCrash = 0;

struct BacktraceState {
	void** current;
	void** end;
};

static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context* context, void* arg) {
	BacktraceState* state = static_cast<BacktraceState*>(arg);
	uintptr_t pc = _Unwind_GetIP(context);

	if (pc == 0) {
		return _URC_NO_REASON;
	}

	if (state->current == state->end) {
		return _URC_END_OF_STACK;
	}

	*state->current++ = reinterpret_cast<void*>(pc);
	return _URC_NO_REASON;
}

static std::string demangle(const char* name) {
	if (name == nullptr) {
		return "";
	}

	int status = 0;
	char* demangled = abi::__cxa_demangle(name, nullptr, nullptr, &status);

	if (status == 0 && demangled != nullptr) {
		std::string result(demangled);
		free(demangled);
		return result;
	}

	if (demangled != nullptr) {
		free(demangled);
	}

	return name;
}

static std::string capture_backtrace() {
	const size_t max_frames = 64;
	void* buffer[max_frames];

	BacktraceState state = { buffer, buffer + max_frames };
	_Unwind_Backtrace(unwind_callback, &state);

	size_t count = state.current - buffer;
	std::ostringstream oss;

	char thread_name[16] = "unknown";
	prctl(PR_GET_NAME, reinterpret_cast<unsigned long>(thread_name), 0, 0, 0);

	uintptr_t threadId = static_cast<uintptr_t>(
        static_cast<unsigned long>(pthread_self())
    );

    oss << "Thread: " << thread_name
        << " (0x" << std::hex << threadId << ")\n";

	// Skip internal crash handler frames (libendercore.so)
	size_t start_frame = 0;

	for (size_t i = 0; i < count; ++i) {
		Dl_info info;

		if (dladdr(buffer[i], &info) && info.dli_fname &&
		    strstr(info.dli_fname, "libendercore.so")) {
			start_frame = i + 1;
		} else if (start_frame > 0) {
			break;
		}
	}

	for (size_t i = start_frame; i < count; ++i) {
		void* addr = buffer[i];
		Dl_info info;

		oss << "#"
		    << std::dec << std::setw(2) << std::setfill('0')
		    << (i - start_frame)
		    << " pc ";

		if (dladdr(addr, &info) && info.dli_fname) {
			const char* libname = strrchr(info.dli_fname, '/');
			libname = libname ? libname + 1 : info.dli_fname;

			uintptr_t rel_pc =
			    reinterpret_cast<uintptr_t>(addr) -
			    reinterpret_cast<uintptr_t>(info.dli_fbase);

			oss << std::hex << rel_pc << " " << libname;

			if (info.dli_sname) {
				uintptr_t symbol_offset =
				    reinterpret_cast<uintptr_t>(addr) -
				    reinterpret_cast<uintptr_t>(info.dli_saddr);

				oss << " (" << demangle(info.dli_sname)
				    << "+0x" << std::hex << symbol_offset << ")";
			}

			oss << "\n";
		} else {
			oss << "0x"
			    << std::hex << std::setw(8) << std::setfill('0')
			    << reinterpret_cast<uintptr_t>(addr)
			    << " unknown\n";
		}
	}

	return oss.str();
}

static void startFatalActivity(const char* message, const char* stackTrace) {
	if (g_jvm == nullptr) {
		LOGE("Cannot report native crash: JavaVM is null");
		return;
	}

	JNIEnv* env = nullptr;
	bool attached = false;

	jint envResult = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

	if (envResult == JNI_EDETACHED) {
		if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) {
			LOGE("Failed to attach current thread for crash reporting");
			return;
		}

		attached = true;
	} else if (envResult != JNI_OK) {
		LOGE("Failed to get JNI environment for crash reporting");
		return;
	}

	if (env == nullptr || g_crashHandlerClass == nullptr || g_onNativeCrashMethod == nullptr) {
		LOGE("CrashHandler is not initialized");
		goto cleanup;
	}

	{
		jstring reason = env->NewStringUTF(message ? message : "");
		jstring trace = env->NewStringUTF(stackTrace ? stackTrace : "");

		if (reason == nullptr || trace == nullptr) {
			LOGE("Failed to allocate crash report strings");

			if (env->ExceptionCheck()) {
				env->ExceptionClear();
			}

			if (reason != nullptr) {
				env->DeleteLocalRef(reason);
			}

			if (trace != nullptr) {
				env->DeleteLocalRef(trace);
			}

			goto cleanup;
		}

		env->CallStaticVoidMethod(g_crashHandlerClass, g_onNativeCrashMethod, reason, trace);

		if (env->ExceptionCheck()) {
			LOGE("Exception while reporting native crash");
			env->ExceptionClear();
		}

		env->DeleteLocalRef(reason);
		env->DeleteLocalRef(trace);
	}

cleanup:
	if (attached) {
		g_jvm->DetachCurrentThread();
	}
}

static const char* signalName(int signum) {
	switch (signum) {
		case SIGSEGV: return "SIGSEGV";
		case SIGABRT: return "SIGABRT";
		case SIGFPE:  return "SIGFPE";
		case SIGILL:  return "SIGILL";
		case SIGBUS:  return "SIGBUS";
		case SIGTRAP: return "SIGTRAP";
		default:      return "UNKNOWN";
	}
}

template <typename T>
static void dumpReg(std::ostringstream& out, const char* name, T value, int width) {
	out << std::left << std::setw(5) << std::setfill(' ') << name
	    << ": 0x"
	    << std::right << std::hex << std::setw(width) << std::setfill('0')
	    << static_cast<uint64_t>(value);
}

static void endReg(std::ostringstream& out, size_t index) {
	out << (index % 2 ? "\n" : "    ");
}

static void dumpRegisters(std::ostringstream& out, ucontext_t* uc) {
	out << "Registers:\n";

	if (uc == nullptr) {
		out << "ucontext is null\n";
		return;
	}

#if defined(__arm__)
	struct Reg {
		const char* name;
		uint32_t value;
	};

	const Reg regs[] = {
		{ "r0",   uc->uc_mcontext.arm_r0   },
		{ "r1",   uc->uc_mcontext.arm_r1   },
		{ "r2",   uc->uc_mcontext.arm_r2   },
		{ "r3",   uc->uc_mcontext.arm_r3   },
		{ "r4",   uc->uc_mcontext.arm_r4   },
		{ "r5",   uc->uc_mcontext.arm_r5   },
		{ "r6",   uc->uc_mcontext.arm_r6   },
		{ "r7",   uc->uc_mcontext.arm_r7   },
		{ "r8",   uc->uc_mcontext.arm_r8   },
		{ "r9",   uc->uc_mcontext.arm_r9   },
		{ "r10",  uc->uc_mcontext.arm_r10  },
		{ "fp",   uc->uc_mcontext.arm_fp   },
		{ "ip",   uc->uc_mcontext.arm_ip   },
		{ "sp",   uc->uc_mcontext.arm_sp   },
		{ "lr",   uc->uc_mcontext.arm_lr   },
		{ "pc",   uc->uc_mcontext.arm_pc   },
		{ "cpsr", uc->uc_mcontext.arm_cpsr }
	};

	const size_t regCount = sizeof(regs) / sizeof(regs[0]);

	for (size_t i = 0; i < regCount; ++i) {
		dumpReg(out, regs[i].name, regs[i].value, 8);
		endReg(out, i);
	}

#elif defined(__aarch64__)
	for (size_t i = 0; i < 31; ++i) {
		char name[8];
		snprintf(name, sizeof(name), "x%zu", i);

		dumpReg(out, name, uc->uc_mcontext.regs[i], 16);
		endReg(out, i);
	}

	dumpReg(out, "sp", uc->uc_mcontext.sp, 16);
	out << "    ";
	dumpReg(out, "pc", uc->uc_mcontext.pc, 16);
	out << "\n";

	dumpReg(out, "pstate", uc->uc_mcontext.pstate, 16);
	out << "\n";
#else
	out << "Unsupported architecture\n";
#endif
}

static void resetSignalAndRaise(int signum) {
	signal(signum, SIG_DFL);
	raise(signum);
}

static void native_signal_handler(int signum, siginfo_t* info, void* reserved) {
	if (g_handlingCrash) {
		resetSignalAndRaise(signum);
		return;
	}

	g_handlingCrash = 1;

	const char* signame = signalName(signum);
	ucontext_t* uc = static_cast<ucontext_t*>(reserved);

	std::ostringstream report;

	report << "Signal " << signum << " (" << signame << "), Code "
	       << (info ? info->si_code : 0)
	       << " at address "
	       << (info ? info->si_addr : nullptr)
	       << "\n\n";

	dumpRegisters(report, uc);

	LOGE("FATAL NATIVE CRASH: %s", signame);

	std::string trace = capture_backtrace();
	startFatalActivity(report.str().c_str(), trace.c_str());

	resetSignalAndRaise(signum);
}

extern "C" JNIEXPORT void JNICALL
Java_org_endercore_android_utils_CrashHandler_initNative(
	JNIEnv* env,
	jobject thiz,
	jstring package_name,
	jstring activity_name
) {
	if (g_crashHandlerClass == nullptr) {
		jclass localClass = env->FindClass("org/endercore/android/utils/CrashHandler");

		if (localClass) {
			g_crashHandlerClass = static_cast<jclass>(env->NewGlobalRef(localClass));
			env->DeleteLocalRef(localClass);

			g_onNativeCrashMethod = env->GetStaticMethodID(
				g_crashHandlerClass,
				"onNativeCrash",
				"(Ljava/lang/String;Ljava/lang/String;)V"
			);
		}

		if (env->ExceptionCheck()) {
			LOGE("Exception while initializing native crash handler");
			env->ExceptionClear();
		}
	}

	if (g_crashHandlerClass == nullptr || g_onNativeCrashMethod == nullptr) {
		LOGE("Failed to initialize Java crash handler references");
		return;
	}

	struct sigaction sa;
	memset(&sa, 0, sizeof(sa));

	sa.sa_sigaction = native_signal_handler;
	sa.sa_flags = SA_SIGINFO;

	sigemptyset(&sa.sa_mask);
	sigaddset(&sa.sa_mask, SIGSEGV);
	sigaddset(&sa.sa_mask, SIGABRT);
	sigaddset(&sa.sa_mask, SIGFPE);
	sigaddset(&sa.sa_mask, SIGILL);
	sigaddset(&sa.sa_mask, SIGBUS);
	sigaddset(&sa.sa_mask, SIGTRAP);

	sigaction(SIGSEGV, &sa, nullptr);
	sigaction(SIGABRT, &sa, nullptr);
	sigaction(SIGFPE, &sa, nullptr);
	sigaction(SIGILL, &sa, nullptr);
	sigaction(SIGBUS, &sa, nullptr);
	sigaction(SIGTRAP, &sa, nullptr);

	LOGD("Native crash handler initialized");
}
// ======== end crash handler ========