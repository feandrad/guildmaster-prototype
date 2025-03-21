#!/bin/bash

# Navega para o diretório do cliente
cd client

# Cria o diretório de build se não existir
mkdir -p build
cd build

# Compila o cliente
cmake ..
make

# Executa o cliente
./guildmaster_client 