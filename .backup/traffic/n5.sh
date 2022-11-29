#!/bin/bash
while :
do
    curl --output "mp.mp4" http://10.0.0.1:8000/mv.mp4;
    curl --output "mp.mp4" http://10.0.0.2:8000/mv.mp4;
    sleep 2
done