#!/usr/bin/env python
import argparse
import subprocess

from util import produce_rand_fail_const_delay_conn_info_str_and_start_bridges


def run(num_nodes,jar_path,failure_probability,failure_close_wait_seconds,
        output_filename, seconds_to_run):
    
    connection_info_str = (
        produce_rand_fail_const_delay_conn_info_str_and_start_bridges(
            num_nodes,failure_probability,failure_close_wait_seconds))
    
    cmd_vec = [
        'java', '-ea', '-jar', jar_path,connection_info_str,
        str(seconds_to_run),output_filename]

    subprocess.call(cmd_vec)



def run_cli():
    parser = argparse.ArgumentParser(
        'Start a raft ring and check who is leader as it runs, '+
        'while we fail nodes.')
    parser.add_argument(
        '--num_nodes',type=int,help='Number of nodes in our raft ring',
        required=True)
    parser.add_argument(
        '--jar_path',type=str,help='Path to leader_during_term.jar',
        required=True)
    parser.add_argument(
        '--failure_probability',type=float,help='Link failure probability',
        required=True)
    parser.add_argument(
        '--failure_wait',type=float,
        help=('When failing a connection, wait this long (in seconds) ' +
              'before calling close'))
    parser.add_argument(
        '--output_filename',type=str,help='Name of file to save results to',
        required=True)
    parser.add_argument(
        '--seconds_to_run',type=int,help='How long to run experiment for',
        required=True)

    args = parser.parse_args()
    
    run(args.num_nodes,args.jar_path,args.failure_probability,
        args.failure_wait, args.output_filename, args.seconds_to_run)
    
    

if __name__ == '__main__':
    run_cli()
