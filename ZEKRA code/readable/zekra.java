Program zekra {

  private static string inputPathPrefix = "path to formatted circuit inputs";
  public static string outputPath = "path to where to store the generated circuit and test inputs";   
   
  private static int ADJ_LIST_SIZE = 600;
  private static int ADJ_LIST_LEVELS = 4;
  private static int EXECUTIONPATH_LIST_SIZE = 500;
  private static int SHADOWSTACK_MAX_SIZE = 7; 
   
  private F_p[] ADJ_LIST = new F_p[ADJ_LIST_SIZE]; 
  private F_p[][] EXECUTIONPATH_LIST = new F_p[EXECUTIONPATH_LIST_SIZE][3]; 
  private F_p[] SHADOWSTACK = new F_p[SHADOWSTACK_MAX_SIZE]; 
   
  private F_p adjListDigest; 
  // H(executionPath||nonce||randomPadding) 
  private F_p executionPathDigest; 
   
  // note that if SHADOWSTACK_MAX_SIZE > 7 then we need more than 3 bits to represent shadowStackTop 
  private uint_3 shadowStackTop = 0; 
  private uint_11 initialNode; 
  private uint_11 finalNode; 
   
  private F_p nonce; 
  private F_p randomPadding; 
   
  private uint_8[][] eAndR1 = new uint_8[EXECUTIONPATH_LIST_SIZE][2]; 
  private F_p[][] adjListEq = new F_p[EXECUTIONPATH_LIST_SIZE][ADJ_LIST_LEVELS * 2]; 
  private F_p[][] neighborExistsProof = new F_p[EXECUTIONPATH_LIST_SIZE][5]; 
   
  private RAM <F_p> adjListMem; 
  private RAM <F_p> shadowStackMem; 
   
  private uint_11[] dest = new uint_11[EXECUTIONPATH_LIST_SIZE]; 
   
  inputs { 
    initialNode, finalNode, adjListDigest, executionPathDigest, nonce 
  } 
   
  witnesses_AssertRange { 
    EXECUTIONPATH_LIST, ADJ_LIST, adjListEq, neighborExistsProof, randomPadding  
  } 
   
  witnesses { 
    eAndR1, dest  
  } 
   
  public void outsource() { 
    // code executed outside the circuit 
    external { 
        BigInteger state = initialNode.val; 
        for (int i = 0; i < EXECUTIONPATH_LIST_SIZE; i++) { 
          BigInteger[] quotients = new BigInteger[ADJ_LIST_LEVELS]; 
          BigInteger[] remainders = new BigInteger[ADJ_LIST_LEVELS]; 
          BigInteger[] neighbourExistsProofVals = new BigInteger[5]; 
          BigInteger[] eAndR1Vals = new BigInteger[2]; 
           
          for (int j = 0; j < ADJ_LIST_LEVELS; j++) { 
            quotients[j] = BigInteger.ZERO; 
            remainders[j] = BigInteger.ZERO; 
          } 
          for (int j = 0; j < neighbourExistsProofVals.length; j++) { 
            neighbourExistsProofVals[j] = BigInteger.ZERO; 
          } 
          for (int j = 0; j < eAndR1Vals.length; j++) { 
            eAndR1Vals[j] = BigInteger.ZERO; 
          } 
           
          BigInteger destNode = EXECUTIONPATH_LIST[i][1].val; 
          BigInteger pos = destNode.mod(BigInteger.valueOf(8)); 
          BigInteger bucket = destNode.divide(BigInteger.valueOf(8)); 
           
          if (!destNode.equals(BigInteger.valueOf(ADJ_LIST_SIZE))) { 
            BigInteger currNodeAdjList = ADJ_LIST[state.intValue()].val; 
             
            for (int j = 0, k = 0; j < ADJ_LIST_LEVELS; j++, k += 16) { 
              quotients[j] = currNodeAdjList.shiftRight(k).and(BigInteger.valueOf(255)); 
              remainders[j] = currNodeAdjList.shiftRight(k + 8).and(BigInteger.valueOf(255)); 
               
              // if the destination node exists at this level of the adjacency list 
              if (bucket.equals(quotients[j]) && remainders[j].testBit(pos.intValue())) { 
                // e 
                neighbourExistsProofVals[0] = BigInteger.valueOf(2).pow(pos.intValue()); 
                // q1 
                neighbourExistsProofVals[1] = new BigDecimal(remainders[j]).divide(new BigDecimal(neighbourExistsProofVals[0]), RoundingMode.FLOOR).toBigInteger(); 
                // r1 
                neighbourExistsProofVals[2] = remainders[j].mod(neighbourExistsProofVals[0]); 
                // q2 
                neighbourExistsProofVals[3] = neighbourExistsProofVals[1].divide(BigInteger.valueOf(2)); 
                // r2 
                neighbourExistsProofVals[4] = neighbourExistsProofVals[1].mod(BigInteger.valueOf(2)); 
                 
                eAndR1Vals[0] = neighbourExistsProofVals[0]; 
                eAndR1Vals[1] = neighbourExistsProofVals[2]; 
              } 
            } 
          } 
           
          for (int j = 0, k = 0; j < ADJ_LIST_LEVELS; j++, k += 2) { 
            adjListEq[i][k].val = quotients[j]; 
            adjListEq[i][k + 1].val = remainders[j]; 
          } 
          for (int j = 0; j < neighbourExistsProofVals.length; j++) { 
            neighborExistsProof[i][j].val = neighbourExistsProofVals[j]; 
          } 
          for (int j = 0; j < eAndR1Vals.length; j++) { 
            eAndR1[i][j].val = eAndR1Vals[j]; 
          } 
           
          state = destNode; 
        } 
    } 
     
    // ///////////////////////////////////////////////////// 
    // the remaining code is compiled into the ZEKRA circuit 
    adjListMem = INIT_RAM <F_p>(ADJ_LIST); 
    shadowStackMem = INIT_RAM <F_p>(SHADOWSTACK); 
     
    // //////////////////////////////// 
    // check authenticity of adjacency list 
    // pad if not perfectly divisible by 8 
    int rem = ADJ_LIST.length % 8; 
    F_p[] paddedAdjList = new F_p[ADJ_LIST.length + rem]; 
     
    for (int i = 0; i < ADJ_LIST.length; i++) { 
      paddedAdjList[i] = ADJ_LIST[i]; 
    } 
    for (int i = ADJ_LIST.length; i < rem; i++) { 
      paddedAdjList[i] = F_p(0); 
    } 
     
    // several node adjacency lists fit into one field element (compress them) 
    F_p[] compressedAdjList = new F_p[paddedAdjList.length / 4]; 
    for (int i = 0; i < paddedAdjList.length / 4; i++) { 
      for (int j = 0, k = 0; j < 4; j++, k += 64) { 
        compressedAdjList[i] = compressedAdjList[i] + (paddedAdjList[i * 4 + j] * F_p(BigInteger.valueOf(2).pow(k))); 
      } 
    } 
     
    // hash first chunk 
    F_p[] poseidonState = new F_p[9]; 
    poseidonState[0] = F_p("0"); 
    for (int i = 1; i < 9; i++) { 
      poseidonState[i] = F_p(compressedAdjList[i]); 
    } 
     
    poseidonState = PoseidonHash.poseidon_hash_8(poseidonState); 
     
    // hash remaining chunks 
    int chunks = compressedAdjList.length / 8; 
    for (int i = 1; i < chunks; i++) { 
      for (int j = 0; j < 8; j++) { 
        poseidonState[j + 1] = F_p(compressedAdjList[i * 8 + j]) + poseidonState[j + 1]; 
      } 
      poseidonState = PoseidonHash.poseidon_hash_8(poseidonState); 
    } 
     
    F_p tmpDigest = poseidonState[2]; 
    log ( tmpDigest , "digest" ); 
    verifyEq ( tmpDigest , adjListDigest ); 
     
    // //////////////////////////////// 
    // check authenticity of move list 
    // pad if not perfectly divisible by 8 
    // several moves fit into one field element (compress them) - we fit 10 24-bit moves in one field element (we assume the move list is perfectly divisible by 10) 
    F_p[] compressedMoveList = new F_p[EXECUTIONPATH_LIST.length / 10]; 
    for (int i = 0; i < EXECUTIONPATH_LIST.length / 10; i++) { 
      for (int j = 0, k = 0; j < 10; j++, k += 24) { 
        F_p[] move = EXECUTIONPATH_LIST[i * 10 + j]; 
        F_p concatenated = move[0]; 
        concatenated = concatenated + (move[1] * F_p(BigInteger.valueOf(2).pow(2))); 
        concatenated = concatenated + (move[2] * F_p(BigInteger.valueOf(2).pow(13))); 
        compressedMoveList[i] = compressedMoveList[i] + (concatenated * F_p(BigInteger.valueOf(2).pow(k))); 
      } 
    } 
     
    // reserve two field elements for the nonce and random padding field elements 
    int rem2 = (compressedMoveList.length + 2) % 8; 
    F_p[] paddedCompressedMoveList = new F_p[(compressedMoveList.length + 2) + rem2]; 
     
    for (int i = 0; i < compressedMoveList.length; i++) { 
      paddedCompressedMoveList[i] = compressedMoveList[i]; 
    } 
    for (int i = compressedMoveList.length; i < rem2; i++) { 
      paddedCompressedMoveList[i] = F_p(0); 
    } 
    paddedCompressedMoveList[compressedMoveList.length + rem2] = nonce; 
    paddedCompressedMoveList[compressedMoveList.length + rem2 + 1] = randomPadding; 
     
     
    // hash first chunk 
    F_p[] poseidonState2 = new F_p[9]; 
    poseidonState2[0] = F_p("0"); 
    for (int i = 1; i < 9; i++) { 
      poseidonState2[i] = F_p(paddedCompressedMoveList[i]); 
    } 
     
    poseidonState2 = PoseidonHash.poseidon_hash_8(poseidonState2); 
     
    // hash remaining chunks 
    int chunks2 = paddedCompressedMoveList.length / 8; 
    for (int i = 1; i < chunks2; i++) { 
      for (int j = 0; j < 8; j++) { 
        poseidonState2[j + 1] = F_p(paddedCompressedMoveList[i * 8 + j]) + poseidonState2[j + 1]; 
      } 
      poseidonState2 = PoseidonHash.poseidon_hash_8(poseidonState2); 
    } 
     
    F_p tmpDigest2 = poseidonState2[2]; 
    log ( tmpDigest2 , "digest2" ); 
    verifyEq ( tmpDigest2 , executionPathDigest ); 
     
    // start from the initial node 
    uint_11 state = initialNode; 
     
    // should never change from 1 
    F_p valid = F_p(1); 
     
    // verify forward and back edges 
    // ignore empty moves to facilitate dynamic path lengths 
    for (int i = 0; i < EXECUTIONPATH_LIST_SIZE; i++) { 
       
      // move format: jumpkind (2 bits), destination node (11 bits), return address (11 bits) 
      F_p[] move = EXECUTIONPATH_LIST[i]; 
      F_p destNode = move[1]; 
       
      // verify forward edges 
      if (destNode NEQ F_p(ADJ_LIST_SIZE)) { 
        valid = valid + destNode - F_p(dest[i]); 
         
        uint_11 uintDestNode = dest[i]; 
        uint_11 bucket = uintDestNode / uint_4(8); 
        uint_3 pos = uint_3(uintDestNode % uint_4(8)); 
         
        // retrieve the neighbour list of the current node 
        F_p currNodeAdjList = adjListMem[state]; 
         
        // v.t. adjacency equation equals the current node's adjacency list 
        F_p adjListAddsUp = 0; 
        for (int j = 0, k = 0; j < ADJ_LIST_LEVELS * 2; j++, k += 8) { 
          adjListAddsUp = adjListAddsUp + adjListEq[i][j] * F_p(BigInteger.valueOf(2).pow(k)); 
        } 
        adjListAddsUp = adjListAddsUp - currNodeAdjList; 
        valid = valid + adjListAddsUp; 
         
        // v.t. q2 * 2 + r2 = q1 
        F_p q2Correct = neighborExistsProof[i][3] * F_p(2) + neighborExistsProof[i][4] - neighborExistsProof[i][1]; 
        valid = valid + q2Correct; 
         
        // v.t. r2 = 1 
        F_p r2Correct = neighborExistsProof[i][4] - F_p(1); 
        valid = valid + r2Correct; 
         
        // v.t. e = 2**pos 
        F_p eCorrect = F_p(1); 
        for (double j = 0, k = 1; j < 8; j++, k = Math.pow(2, j)) { 
          eCorrect = eCorrect * ((F_p(pos) - F_p(new Double(j).intValue())) + (neighborExistsProof[i][0] - F_p(new Double(k).intValue()))); 
        } 
        valid = valid + eCorrect; 
         
        // v.t. r1 < e 
        F_p r1Correct = 0; 
        r1Correct = r1Correct + (F_p(eAndR1[i][0]) - neighborExistsProof[i][0]); 
        r1Correct = r1Correct + (F_p(eAndR1[i][1]) - neighborExistsProof[i][2]); 
        r1Correct = r1Correct + F_p(uint_1(eAndR1[i][0] > eAndR1[i][1])) - F_p(1); 
        valid = valid + r1Correct; 
         
        // v.t. destNode exists at some level in the adjacency list 
        F_p destNodeExists = F_p(1); 
        for (int j = 0; j < ADJ_LIST_LEVELS * 2; j += 2) { 
           
          // v.t. q1 * e + r1 = rems 
          F_p q1Correct = neighborExistsProof[i][1] * neighborExistsProof[i][0] + neighborExistsProof[i][2] - adjListEq[i][j + 1]; 
           
          // v.t. bucket = destNode / 8 
          F_p bucketMatch = F_p(bucket) - adjListEq[i][j]; 
           
          // v.t. q1Correct and bucketMatch 
          destNodeExists = destNodeExists * (q1Correct + bucketMatch); 
        } 
         
        valid = valid + destNodeExists; 
         
        // update position in the CFG 
        state = uintDestNode; 
      } 
       
      // verify back edges using shadow stack 
      // jmpkind: 00 (jmp), 01 (call), 10 (ret) 
      F_p jmpkind = move[0]; 
      if (jmpkind EQ F_p(1)) { 
        // add caller return address to shadow stack 
        push(move[2]); 
      } else if (jmpkind EQ F_p(2)) { 
        // verify return address integrity 
        F_p shadowAddr = pop(); 
        valid = valid + shadowAddr - destNode; 
      } 
    } 
     
    verifyEq ( valid , F_p(1) ); 
    verifyEq ( state , finalNode ); 
  } 
   
  private F_p pop() { 
    F_p data = 0; 
    if (shadowStackTop NEQ uint_1(0)) { 
      shadowStackTop = shadowStackTop - uint_1(1); 
      data = shadowStackMem[shadowStackTop]; 
    } 
    return data; 
  } 
   
  private void push(F_p data) { 
    verify ( shadowStackTop NEQ uint_11(SHADOWSTACK_MAX_SIZE) ); 
    shadowStackMem[shadowStackTop] = data; 
    shadowStackTop = shadowStackTop + uint_1(1); 
  } 
   
  SampleRun("Sample_Run1", true){ 
    pre { 
        string line; 
        int i = 0; 
        try { 
          BufferedReader br = new BufferedReader(new FileReader(inputPathPrefix + "adjList.txt")); 
          while ((line = br.readLine()) != null) { 
            ADJ_LIST[i].val = new BigInteger(line, 10); 
            i = i + 1; 
          } 
           
          br = new BufferedReader(new FileReader(inputPathPrefix + "executionPath.txt")); 
          i = 0; 
          while ((line = br.readLine()) != null) { 
            BigInteger move = new BigInteger(line, 10); 
            BigInteger jmpkind = move.shiftRight(0).and(BigInteger.valueOf(3)); 
            BigInteger destNode = move.shiftRight(2).and(BigInteger.valueOf(2047)); 
            BigInteger retAddr = move.shiftRight(13).and(BigInteger.valueOf(2047)); 
            EXECUTIONPATH_LIST[i][0].val = jmpkind; 
            EXECUTIONPATH_LIST[i][1].val = destNode; 
            EXECUTIONPATH_LIST[i][2].val = retAddr; 
            dest[i].val = destNode; 
            i = i + 1; 
          } 
           
          br = new BufferedReader(new FileReader(inputPathPrefix + "initialNode.txt")); 
          initialNode.val = new BigInteger(br.readLine(), 10); 
           
          br = new BufferedReader(new FileReader(inputPathPrefix + "finalNode.txt")); 
          finalNode.val = new BigInteger(br.readLine(), 10); 
           
          // AL = 600, EP = 500 
          adjListDigest.val = new BigInteger("14555599622525891295493345517969598444787001596926119339380051831433142747110"); 
          // AL = 300, EP = 500 
          // adjListDigest.val = new BigInteger("12410481772639775116854743377933890941291255303600460284346134842123947938967"); 
          // AL = 600, EP = 250 
          // adjListDigest.val = new BigInteger("14555599622525891295493345517969598444787001596926119339380051831433142747110"); 
          // AL = 300, EP = 250 
          // adjListDigest.val = new BigInteger("12410481772639775116854743377933890941291255303600460284346134842123947938967"); 
           
          nonce.val = BigInteger.ZERO; 
          randomPadding.val = BigInteger.ZERO; 
           
          // AL = 600, EP = 500 
          executionPathDigest.val = new BigInteger("78441658254016821114038761496542762100719328405848532215530403216147788298"); 
          // AL = 300, EP = 500 
          // executionPathDigest.val = new BigInteger("15740099005144919104495159150964911922114641479879597777605068284686374404430"); 
          // // AL = 600, EP = 250 
          // executionPathDigest.val = new BigInteger("7361114549986891199743627005350890791190710356564785606608602527389321519772"); 
          // AL = 300, EP = 250 
          // executionPathDigest.val = new BigInteger("9784835613083345840552694905839718451088415244716598982157336451337259165077"); 
           
        } catch (Exception ex) { 
          System.out.println(ex.getMessage().toString()); 
        } 
    } 
    post { 
        <no statements> 
    } 
  } 
   
   
  public static void main(string[] args) { 
    Config.multivariateExpressionMinimization = false; 
    Config.arithOptimizerIncrementalMode = true; 
    Config.outputVerbose = true; 
    Config.inputVerbose = false; 
    Config.debugVerbose = true; 
    Config.writeCircuits = true; 
    Config.outputFilesPath = outputPath; 
  } 
}