#!/bin/bash

# Configurações padrão
TCP_PORT=9999
UDP_PORT=9998

# Processa argumentos de linha de comando
while getopts "t:u:" opt; do
  case $opt in
    t) TCP_PORT=$OPTARG ;;
    u) UDP_PORT=$OPTARG ;;
    \?) echo "Uso inválido: -$OPTARG" >&2; exit 1 ;;
  esac
done

# Navegue para o diretório do servidor
cd server

# Defina as propriedades do sistema para as portas
JAVA_OPTS="-Dtcp.port=$TCP_PORT -Dudp.port=$UDP_PORT"

# Compila e executa o servidor
echo "Iniciando servidor nas portas: TCP=$TCP_PORT, UDP=$UDP_PORT"
./gradlew run --args="$JAVA_OPTS" 