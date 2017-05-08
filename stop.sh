#!/bin/bash
ps -ef | grep play | grep -v grep | awk '{print $2}' | xargs -r kill -9
