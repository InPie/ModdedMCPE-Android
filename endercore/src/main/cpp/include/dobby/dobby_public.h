#include <elf.h>

#ifdef __LP64__
typedef Elf64_Sym Elf_Sym;
    typedef Elf64_Addr Elf_Addr;
    typedef Elf64_Phdr Elf_Phdr;
    typedef Elf64_Dyn Elf_Dyn;
    typedef Elf64_Rel Elf_Rel;
    typedef Elf64_Rela Elf_Rela;
#else
    typedef Elf32_Sym Elf_Sym;
    typedef Elf32_Addr Elf_Addr;
    typedef Elf32_Phdr Elf_Phdr;
    typedef Elf32_Dyn Elf_Dyn;
    typedef Elf32_Rel Elf_Rel;
    typedef Elf32_Rela Elf_Rela;
#endif

#ifdef __cplusplus
    extern "C" {
#endif
    void* dobby_dlsym(void* handle, const char* symbol);
    Elf_Sym* dobby_elfsym(void* si, const char* name);
#ifdef __cplusplus
    }
#endif