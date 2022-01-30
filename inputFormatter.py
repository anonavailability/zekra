outDir = '' # where to store the formatted inputs
pathPrefix = 'test datasets/' # location of adjacency list and recorded execution path
# adjListFileIn = pathPrefix + 'adjList300.txt'
adjListFileIn = pathPrefix + 'adjList600.txt' # the considered program's adjacency list
executionPathFileIn = pathPrefix + 'executionPath.txt' # the recorded execution path

initialNode = 0 # starting node in the execution path
finalNode = 599 # end node in the execution path
maxExecutionPathLen = 500 # maximum length of the execution path (if the recorded execution path is less than 500, it is padded with maxExecutionPathLen-len(execution path) empty transitions)

executionPath = []
adjListBin = []
adjList = []

# for an adjacency list containing 600 nodes (first node has ID 0), totalNodes is set to 600
# used to pad empty transitions to the execution path
totalNodes = 0

with open(adjListFileIn, 'r') as adjlistIn:
    for line in adjlistIn.readlines():
        totalNodes += 1
        adjlist = list(line.replace('\n', '').split(' '))
        neighbourList = adjlist[1:]

        mapping = {}
        for edgeId in neighbourList: # dividend
            edgeId = int(edgeId)
            bucket = int(edgeId / 8) # quotient
            pos = edgeId % 8 # remainder
            # dividend = divisor (8) * quotient + remainder
            if bucket not in mapping:
                mapping[bucket] = 0
            mapping[bucket] |= (1 << pos) # set bit        

        # each node currently supports 4 sets (buckets) of neighbour IDs < 2048, 
        # where nodes in each set (of 8 bits) share the same quotient
        if len(mapping) > 4:
            print('NEED TOO MANY BUCKETS')
            exit(0)

        if len(mapping) == 0:
            print('Exit node (no neighbours): %s' %adjlist)

        neighbours = ''
        for bucket in mapping:
            neighbours += '%s%s' %(format(mapping[bucket], '08b'), format(bucket, '08b')) # 16 bits

        if len(neighbours) == 0:
            neighbours = '0'

        adjListBin.append(format(int(neighbours, 2), '064b'))

for node in adjListBin:
    adjList.append(int(node, 2))

with open(executionPathFileIn, 'r') as executionPathIn:
    for line in executionPathIn.readlines():
        move = line.replace('\n', '').split(' ')
        jmpkind = format(0, '02b')
        destNode = format(int(move[1]), '011b')
        retAddr = format(0, '011b')
        if move[0] == 'call':
            jmpkind = format(1, '02b')
            retAddr = format(int(move[2]), '011b')
        elif move[0] == 'ret':
            jmpkind = format(2, '02b')
        moveFormatted = retAddr + destNode + jmpkind
        executionPath.append(int(moveFormatted, 2))

# append empty moves
while len(executionPath) < maxExecutionPathLen:
    emptyMoveFormatted = format(0, '011b') + format(totalNodes, '011b') + format(0, '02b')
    executionPath.append(int(emptyMoveFormatted, 2))

# generate circuit inputs
with open(outDir + 'adjList.txt', 'w') as adjListOut:
    output = ''
    for node in adjList:
        # print(format(node, '064b'), node)
        output += '%s\n' %node
    adjListOut.write('%s' %output.rstrip('\n'))

with open(outDir + 'executionPath.txt', 'w') as executionPathOut:
    output = ''
    for move in executionPath:
        output += '%s\n' %move
    executionPathOut.write('%s' %output.rstrip('\n'))

with open(outDir + 'initialNode.txt', 'w') as initialNodeOut:
    initialNodeOut.write(str(initialNode))

with open(outDir + 'finalNode.txt', 'w') as finalNodeOut:
    finalNodeOut.write(str(finalNode))

print('nodes: %s\t adjacency list: %s\t execution path len: %s' %(totalNodes, len(adjList), len(executionPath)))
