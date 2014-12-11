#!/usr/bin/env python
import argparse
import subprocess

from util import produce_flapping_info_str_and_start_bridges

# FIXME: Currently hard-code number of nodes in ring.

def run(jar_path,output_filename,seconds_to_run):

    connection_info_str = (
        produce_flapping_info_str_and_start_bridges())

    cmd_vec = [
        'java', '-ea', '-jar', jar_path,connection_info_str,
        str(seconds_to_run),output_filename]

    subprocess.call(cmd_vec)


def run_cli():
    parser = argparse.ArgumentParser(
        'Start a raft ring and check who is leader as it runs, '+
        'while we fail nodes.')
    parser.add_argument(
        '--jar_path',type=str,help='Path to leader_during_term.jar',
        required=True)
    parser.add_argument(
        '--output_filename',type=str,help='Name of file to save results to',
        required=True)
    parser.add_argument(
        '--seconds_to_run',type=int,help='How long to run experiment for',
        required=True)

    
    args = parser.parse_args()
    
    run(args.jar_path,args.output_filename,args.seconds_to_run)
    

if __name__ == '__main__':
    run_cli()
