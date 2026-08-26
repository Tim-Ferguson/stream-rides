#!/bin/zsh
DIR="${0:A:h}"
"$DIR/saro-control" split-hulu
echo
echo "Done. You can close this window."
read -k 1 "?Press any key to close."
