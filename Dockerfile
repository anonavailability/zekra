FROM ubuntu:16.04

RUN apt update -y && apt install -y build-essential cmake git libgmp3-dev libprocps4-dev python-markdown libboost-all-dev libssl-dev pkg-config

WORKDIR /usr/src/

RUN git clone https://github.com/akosba/libsnark.git

COPY libsnarkChanges /usr/src/libsnark

WORKDIR /usr/src/libsnark

RUN git submodule init && git submodule update
RUN mkdir build

WORKDIR /usr/src/libsnark/build

RUN cmake -DWITH_SUPERCOP=OFF ..
RUN make
