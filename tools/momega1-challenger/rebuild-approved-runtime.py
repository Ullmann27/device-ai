#!/usr/bin/env python3
"""Independent reproduction of the single owner-approved official CPU runtime.
Run only after the author's compile finishes; max2 jobs, no source modification.
"""
import hashlib,json,pathlib,subprocess,time
root=pathlib.Path('/workspace/momega1-local-model');source=root/'llama.cpp';build=root/'challenger-build';evidence=root/'challenger-build-evidence';evidence.mkdir(mode=0o700,exist_ok=True)
cmake=root/'build-tools/cmake-wheel/cmake/data/bin/cmake'
def git(*args):return subprocess.check_output(['git','-C',str(source),*args],text=True).strip()
assert git('rev-parse','HEAD')=='6cb3e672e8d10fd4feab1a048bf2967c40623133'
assert git('rev-parse','HEAD^{tree}')=='42065e172024f95eb242763caa06812c09d363e7'
assert not git('status','--porcelain=v1','--untracked-files=all')
assert hashlib.sha256(cmake.read_bytes()).hexdigest()=='57452fa01ed0f69552c06d3e25edd0d9375347b982dfb709393526cb45396d6b'
args=[str(cmake),'-S',str(source),'-B',str(build),'-DCMAKE_BUILD_TYPE=Release','-DBUILD_SHARED_LIBS=OFF','-DGGML_BACKEND_DL=OFF','-DGGML_NATIVE=OFF','-DGGML_OPENMP=OFF','-DGGML_BLAS=OFF','-DGGML_CUDA=OFF','-DGGML_VULKAN=OFF','-DLLAMA_BUILD_TESTS=OFF','-DLLAMA_BUILD_EXAMPLES=OFF','-DLLAMA_BUILD_SERVER=OFF','-DLLAMA_BUILD_APP=OFF','-DLLAMA_BUILD_TOOLS=ON','-DLLAMA_OPENSSL=OFF','-DLLAMA_SUBPROCESS=OFF','-DLLAMA_CONNECT=OFF','-DLLAMA_LLGUIDANCE=OFF',f'-DCMAKE_C_FLAGS=-ffile-prefix-map={source}=/llama.cpp -fdebug-prefix-map={source}=/llama.cpp',f'-DCMAKE_CXX_FLAGS=-ffile-prefix-map={source}=/llama.cpp -fdebug-prefix-map={source}=/llama.cpp']
record={'sourceCommit':git('rev-parse','HEAD'),'sourceTree':git('rev-parse','HEAD^{tree}'),'configureCommand':args,'maxCompileJobs':2}
started=time.monotonic()
with (evidence/'configure.log').open('w') as f:p=subprocess.run(args,stdout=f,stderr=subprocess.STDOUT)
record['configureExit']=p.returncode
if not p.returncode:
 command=[str(cmake),'--build',str(build),'--target','llama-completion','--parallel','2'];record['buildCommand']=command
 with (evidence/'build.log').open('w') as f:p=subprocess.run(command,stdout=f,stderr=subprocess.STDOUT)
 record['buildExit']=p.returncode
 if not p.returncode:
  binary=build/'bin/llama-completion';author=root/'build-cpu/bin/llama-completion'
  record['binaryPath']=str(binary);record['binaryBytes']=binary.stat().st_size;record['binarySha256']=hashlib.sha256(binary.read_bytes()).hexdigest();record['authorBinarySha256']=hashlib.sha256(author.read_bytes()).hexdigest();record['byteIdenticalToAuthor']=binary.read_bytes()==author.read_bytes()
record['elapsedSeconds']=round(time.monotonic()-started,3);record['sourceCleanAfter']=not git('status','--porcelain=v1','--untracked-files=all')
(evidence/'rebuild.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record))
raise SystemExit(p.returncode)
