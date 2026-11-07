/* Independent process fixture. NO model inference, NO real model weights.
 * Compile with -DMODE=N. Only disposable synthetic files may be supplied.
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/resource.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <net/if.h>
#ifndef MODE
#define MODE 0
#endif
static const char *reply="{\"plan\":[\"Correct synthetic integer bound\"],\"hypothesis\":\"Synthetic boundary omits final unit\",\"patches\":[{\"file\":\"lib/count.mjs\",\"oldText\":\"return n;\",\"newText\":\"return n + 1;\"}]}";
int main(int argc,char **argv){
 prctl(PR_SET_NAME,"momega1-probe",0,0,0);
 if(MODE==1){signal(SIGTERM,SIG_IGN);for(;;)pause();}
 if(MODE==2){for(int i=0;i<200000;i++)putchar('x');return 0;}
 if(MODE==3)return 23;
 if(MODE==4){if(fork()==0){signal(SIGTERM,SIG_IGN);for(;;)pause();}signal(SIGTERM,SIG_IGN);for(;;)pause();}
 if(MODE==5){struct if_nameindex *ifs=if_nameindex();if(!ifs)return 40;for(struct if_nameindex *p=ifs;p->if_index;p++)if(strcmp(p->if_name,"lo")!=0)return 41;if_freenameindex(ifs);}
 if(MODE==6){struct rlimit r;if(getrlimit(RLIMIT_AS,&r)||r.rlim_cur==RLIM_INFINITY)return 42;}
 if(MODE==7){struct rlimit r;if(getrlimit(RLIMIT_NPROC,&r)||r.rlim_cur==RLIM_INFINITY)return 43;}
 if(MODE==8){const char *model=NULL;for(int i=1;i<argc-1;i++)if(!strcmp(argv[i],"-m")||!strcmp(argv[i],"--model"))model=argv[i+1];if(!model)return 44;int fd=open(model,O_RDONLY);if(fd<0)return 45;char magic[4];if(read(fd,magic,4)!=4||memcmp(magic,"GGUF",4))return 46;close(fd);}
 puts(reply);return 0;
}
