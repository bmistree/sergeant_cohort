#!/usr/bin/env python
import argparse
import os
import sys
CURRENT_DIRECTORY = os.path.dirname(os.path.abspath(__file__))
INTERCEPTOR_DIRECTORY = os.path.join(
    CURRENT_DIRECTORY,'..','..','deps','interceptor')

BASE_TCP_PORT = 2222
def get_unique_tcp_port ():
    '''
    Assuming program is single-threaded.
    '''
    global BASE_TCP_PORT
    BASE_TCP_PORT += 1
    return BASE_TCP_PORT


class RemoteDictElement(object):
    def __init__(self,real_local_port,real_remote_port,
                 bridge_to_local_port, bridge_to_remote_port):
        self.real_local_port = real_local_port
        self.real_remote_port = real_remote_port
        self.bridge_to_local_port = bridge_to_local_port
        self.bridge_to_remote_port = bridge_to_remote_port

    def get_reversed(self):
        return RemoteDictElement(
            self.real_remote_port,self.real_local_port,
            self.bridge_to_remote_port,self.bridge_to_local_port)

    def produce_java_arg_str(self,remote_id):
        '''
        @returns {str}  of the form:
            host,port,other_id,host,port

        where first host/port is local and second is remote (really,
        bridge to remote port.
        '''
        to_format_to_return =(
            '127.0.0.1,%(local_port)d,%(remote_cohort_id)d,' +
            '127.0.0.1,%(bridge_to_remote_port)d')
        return (to_format_to_return %
                {
                    'local_port': self.real_local_port,
                    'remote_cohort_id': remote_id,
                    'bridge_to_remote_port': self.bridge_to_remote_port
                })

    def start_bridge(self,failure_probability):
        '''
        Only starts bridge in one direction: any connection to
        bridge_remote should get intercepted and redirected to
        real_remote
        '''
        assert False

        

class CohortConnectionInfo(object):
    def __init__(self,local_cohort_id):
        self.local_cohort_id = local_cohort_id
        # keys are remote cohort ids, values are remote cohort ports.
        self.remote_dict = {}
        
    def add_remote(self,remote_cohort_id,remote_dict_element):
        self.remote_dict[remote_cohort_id] = remote_dict_element

    def get_reversed(self,remote_cohort_id):
        if remote_cohort_id in self.remote_dict:
            return self.remote_dict[remote_cohort_id].get_reversed()
        return None

    def produce_java_arg_str(self):
        '''
        @returns {str} of the form:
        
        local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  | 
        '''
        to_return = str(self.local_cohort_id) + ':'

        for remote_id in self.remote_dict:
            remote_dict_element = self.remote_dict[remote_id]
            to_return += (
                remote_dict_element.produce_java_arg_str(remote_id) + ';')
        
        return to_return


        
def produce_connection_info_str_and_start_bridges(num_nodes,failure_probability):
    '''
    @param {int} num_nodes --- Number of nodes to run in raft ring.

    @returns {str} --- Of the form:
    
          local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  | 
          local_id: host,port,other_id,host,port;
                  host,port,other_id,host,port; ...  |

    where everything between a | is the connection info for a single
    node.
    '''
    connection_info_dict = {}
    
    for i in range(0,num_nodes):
        local_cohort_id = i
        local_connection_info = CohortConnectionInfo(local_cohort_id)
        connection_info_dict[local_cohort_id] = local_connection_info

        for j in range(0,num_nodes):
            remote_cohort_id = j
            if local_cohort_id == remote_cohort_id:
                continue

            if remote_cohort_id in connection_info_dict:
                remote_connection_info = connection_info_dict[remote_cohort_id]
                to_add = remote_connection_info.get_reversed(local_cohort_id)
                if to_add is None:
                    assert False
            else:
                to_add = RemoteDictElement(
                    get_unique_tcp_port(), get_unique_tcp_port(),
                    get_unique_tcp_port(), get_unique_tcp_port())
                
            local_connection_info.add_remote(remote_cohort_id,to_add)


    # FIXME: Still need to start bridges

    to_return = ''
    for local_cohort_id in connection_info_dict:
        local_connection_info = connection_info_dict[local_cohort_id]
        to_return += local_connection_info.produce_java_arg_str() + "|" + '\n'

    return to_return
        

def run(num_nodes,jar_path,failure_probability,output_filename,
        seconds_to_run):
    
    connection_info_str = (
        produce_connection_info_str_and_start_bridges(
            num_nodes,failure_probability))
    
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
        '--jar_path',type=string,help='Path to leader_during_term.jar',
        required=True)
    parser.add_argument(
        '--failure_probability',type=float,help='Link failure probability',
        required=True)
    parser.add_argument(
        '--output_filename',type=str,help='Name of file to save results to',
        required=True)
    parser.add_argument(
        '--seconds_to_run',type=int,help='How long to run experiment for',
        required=True)

    argparse.parse_args()
    
    run(parser.num_nodes,parser.jar_path,parser.failure_probability,
        parser.output_filename, parser.seconds_to_run)
    
    

if __name__ == '__main__':
    #run_cli()
    print '\n\n'
    print produce_connection_info_str_and_start_bridges(2,.1)
    print '\n\n'
