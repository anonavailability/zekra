# ZEKRA

This repository accompanies the paper "ZEKRA: ZEro-Knowledge Runtime Attestation".

## Contents

- `libsnarkChanges`: Modifications to [libsnark](https://github.com/akosba/libsnark.git) to add support for writing proofs and the generated circuit's verification and proving keys to file. 
- `test datasets`: Synthetic program CFGs and execution path datasets used as input to the ZEKRA circuit during performance evaluation.
    - `adjList300.txt`: Adjacency list containing 300 nodes.
    - `adjList600.txt`: Adjacency list containing 600 nodes.
    - `executionPath.txt`: A valid execution path according to the adjacency list.
- `ZEKRA code`: ZEKRA program code.
    - `readable`: Folder containing the ZEKRA program code in a readable format (without having to install [xjsnark](https://github.com/akosba/xjsnark) and [JetBrains MPS 3.3](https://confluence.jetbrains.com/display/MPS/JetBrains+MPS+3.3+Download+Page)).
    - `zekra.mps`: The actual ZEKRA program code written using the [xjsnark](https://github.com/akosba/xjsnark) library.
- `docker-compose.yml`: File for executing a command in the environment defined in `Dockerfile`.
- `Dockerfile`: Build file for setting up a containerized execution environment for running [xjsnark](https://github.com/akosba/xjsnark) output circuits on [libsnark](https://github.com/akosba/libsnark.git) (using the changes in `libsnarkChanges`)
- `inputFormatter.py`: A Python script to format `test datasets` as inputs to the ZEKRA circuit.

## Requirements

- [Docker](https://docker.com/)

## Preparing inputs to the ZEKRA circuit

To format inputs for the ZEKRA program (`zekra.mps`), first open `inputFormatter.py` and change the following lines:

    outDir = '' # where to store the formatted inputs
    pathPrefix = 'test datasets/' # location of adjacency list and recorded execution path
    adjListFileIn = pathPrefix + 'adjList600.txt' # the considered program's adjacency list
    executionPathFileIn = pathPrefix + 'executionPath.txt' # the recorded execution path
    ...
    initialNode = 0 # starting node in the execution path
    finalNode = 599 # end node in the execution path
    maxExecutionPathLen = 500 # maximum length of the execution path (if the recorded execution path is less than 500, it is padded with maxExecutionPathLen-len(execution path) empty transitions)

After adjusting the configuration, execute the script:

    python inputFormatter.py

This will output the following files: 

- `outDir/adjList.txt`: contains the encoded adjacency list
- `outDir/executionPath.txt`: contains the encoded execution path (padded if needed)
- `outDir/initialNode.txt`: contains the value of `initialNode`
- `outDir/finalNode.txt`: contains the value of `finalNode`

## Compiling the ZEKRA circuit

To edit or compile the ZEKRA program code (`zekra.mps`):

- Download or clone [xjsnark](https://github.com/akosba/xjsnark), follow the installation instructions, and install [JetBrains MPS 3.3](https://confluence.jetbrains.com/display/MPS/JetBrains+MPS+3.3+Download+Page).
- Copy `zekra.mps` to "[xjsnark](https://github.com/akosba/xjsnark)/languages/xjsnark/sandbox/models/xjsnark". 
- Open the [xjsnark](https://github.com/akosba/xjsnark) project in [JetBrains MPS 3.3](https://confluence.jetbrains.com/display/MPS/JetBrains+MPS+3.3+Download+Page).
- Right-click `xjsnark` in the project viewer and click "Rebuild Language 'xjsnark'".
- Expand the `zekra` module under `xjsnark.sandbox` and open the `zekra.zekra` program.
- Modify the following lines as needed (set `inputPathPrefix` to the location of `outDir`):
```
private static string inputPathPrefix = "path to formatted circuit inputs"; 
public static string outputPath = "path to where to store the generated circuit and test inputs";
```
- Modify the following lines as needed:
```
private static int ADJ_LIST_SIZE = 600;  
private static int ADJ_LIST_LEVELS = 4; 
private static int EXECUTIONPATH_LIST_SIZE = 500; 
private static int SHADOWSTACK_MAX_SIZE = 7;
```
- Under `SampleRun`, modify the following lines as needed:
```
adjListDigest.val = new BigInteger("14555599622525891295493345517969598444787001596926119339380051831433142747110");
...
nonce.val = BigInteger.ZERO; 
randomPadding.val = BigInteger.ZERO;
...
executionPathDigest.val = new BigInteger("78441658254016821114038761496542762100719328405848532215530403216147788298");
```
- Right-click the `zekra` module and click "Rebuild Model 'xjsnark.zekra'".
- Right-click the `zekra.zekra` program and click "Run 'Class zekra'", which will generate the following output files:
    - `outputPath/zekra.arith`: containing the ZEKRA arithmetic circuit.
    - `outputPath/zekra_Sample_Run1.in`: containing the inputs to the ZEKRA arithmetic circuit.

## Executing the ZEKRA circuit

To execute the ZEKRA circuit using [libsnark](https://github.com/akosba/libsnark.git), use the accompanying docker files, which will use the built-in profiler to measure the performance of using the Groth16 proof system to: generate the circuit's proving and verification keys, generate a proof using the generated circuit inputs, and verify the generated proof.

### Running the profiler (for evaluation):

- Copy the files '`outputPath/zekra.arith`' and '`outputPath/zekra_Sample_Run1.in`' to the base directory of this repository (i.e., where `docker-compose.yml` is stored).
- Open a terminal and run `docker-compose.yml`:
```
docker-compose up --build zekra
```
- This will simultaneously output the following files to the base of this repository:
    - `pk_file`: The circuit's public proving key.
    - `vk_file`: The circuit's public verification key.
    - `proof_file`: The generated proof containing the Groth16 elements and the public circuit inputs.
