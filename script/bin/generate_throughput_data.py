#!/usr/bin/env python
import argparse
import subprocess

from util import produce_const_delay_conn_info_str_and_start_bridges

# Currently hard-coded
NUM_NODES = 3


def run(jar_path,connection_delay_seconds,output_filename,
        num_appends,heartbeat_timeout_period_ms):

    connection_info_str = (
        produce_const_delay_conn_info_str_and_start_bridges(
            NUM_NODES,connection_delay_seconds))
    
    cmd_vec = [
        'java', '-ea', '-jar', jar_path,str(num_appends),
        connection_info_str,output_filename,str(heartbeat_timeout_period_ms)]

    subprocess.call(cmd_vec)



def run_cli():
    parser = argparse.ArgumentParser(
        'Start a raft ring and check who is leader as it runs, '+
        'while we fail nodes.')
    parser.add_argument(
        '--jar_path',type=str,help='Path to leader_during_term.jar',
        required=True)
    parser.add_argument(
        '--connection_delay_seconds',type=float,
        help=('How long to delay messages sent between cohort nodes.'))
    parser.add_argument(
        '--output_filename',type=str,help='Name of file to save results to',
        required=True)
    parser.add_argument(
        '--num_appends',type=int,help='Number of entries to append to list',
        required=True)
    parser.add_argument(
        '--heartbeat_timeout_period_ms',type=int,
        help='How many ms to wait before timing out heartbeat',
        required=True)

    
    args = parser.parse_args()
    
    run(args.jar_path,args.connection_delay_seconds,args.output_filename,
        args.num_appends,args.heartbeat_timeout_period_ms)
    

if __name__ == '__main__':
    run_cli()
