#!/usr/bin/env python
import argparse
import subprocess
import os
import sys
CURRENT_DIRECTORY = os.path.dirname(os.path.abspath(__file__))
INTERCEPTOR_DIRECTORY = os.path.join(
    CURRENT_DIRECTORY,'..','..','deps','interceptor')
sys.path.append(INTERCEPTOR_DIRECTORY)

from interceptor.util import HostPortPair
from interceptor.plan import (
    RandomFailConstantDelayPlan, ConstantDelayPlan, PassThroughPlan,DropPlan)
from interceptor.bridge import Bridge


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

    def start_bridge(self,plan_factory):
        '''
        Only starts bridge in one direction: any connection to
        bridge_remote should get intercepted and redirected to
        real_remote
        '''
        intercepting = HostPortPair('127.0.0.1',self.bridge_to_remote_port)
        to_connect_to = HostPortPair('127.0.0.1',self.real_remote_port)

        plan_one_side = plan_factory.construct_plan()
        plan_other_side = plan_factory.construct_plan()
        
        bridge = Bridge(
            intercepting,plan_one_side,
            to_connect_to,plan_other_side)
        bridge.non_blocking_connection_setup()

        

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

    def start_all_bridges(self,plan_factory):
        for remote_id in self.remote_dict:
            remote_dict_element = self.remote_dict[remote_id]
            remote_dict_element.start_bridge(plan_factory)

    def start_all_bridges_remote_specific(self,plan_factory_dict):
        '''
        @param {dict} plan_factory_dict --- Keys are remote cohort
        ids.  Values are plan factories to use for them.
        '''
        for remote_id in self.remote_dict:
            remote_dict_element = self.remote_dict[remote_id]
            plan_factory = plan_factory_dict.get(remote_id,None)
            #### DEBUG
            if plan_factory is None:
                print ('Missing remote in plan factory dict')
                assert False
            #### END DEBUG

            remote_dict_element.start_bridge(plan_factory)
            
    
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

class PlanFactory(object):
    '''
    Base class
    '''
    def construct_plan(self):
        pass

class PassThroughPlanFactory(PlanFactory):
    def construct_plan(self):
        return PassThroughPlan()

class DropPlanFactory(PlanFactory):
    def construct_plan(self):
        return DropPlan()
    
class RandomFailConstantDelayPlanFactory(PlanFactory):
    def __init__(self,failure_probability,failure_close_wait_seconds):
        self.failure_probability = failure_probability
        self.failure_close_wait_seconds = failure_close_wait_seconds

    def construct_plan(self):
        return RandomFailConstantDelayPlan(
            self.failure_probability,self.failure_close_wait_seconds)

class ConstantDelayPlanFactory(PlanFactory):
    def __init__(self,seconds_to_delay_before_forwarding):
        self.seconds_to_delay_before_forwarding = (
            seconds_to_delay_before_forwarding)
    
    def construct_plan(self):
        return ConstantDelayPlan(self.seconds_to_delay_before_forwarding)
    
    
def produce_rand_fail_const_delay_conn_info_str_and_start_bridges(
    num_nodes,failure_probability,failure_close_wait_seconds):

    plan_factory = RandomFailConstantDelayPlanFactory(
        failure_probability,failure_close_wait_seconds)
    
    return _produce_connection_info_str_and_start_bridges(
        num_nodes,plan_factory)


def produce_direct_connect_conn_info_str(num_nodes):
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
                local_port = get_unique_tcp_port()
                remote_port = get_unique_tcp_port()
                to_add = RemoteDictElement(
                    local_port,remote_port,local_port,remote_port)
                
            local_connection_info.add_remote(remote_cohort_id,to_add)

    to_return = ''
    for local_cohort_id in connection_info_dict:
        local_connection_info = connection_info_dict[local_cohort_id]
        to_return += local_connection_info.produce_java_arg_str() + "|"

    return to_return


def produce_const_delay_conn_info_str_and_start_bridges(
    num_nodes,connection_delay_seconds):

    plan_factory = ConstantDelayPlanFactory(connection_delay_seconds)
    
    return _produce_connection_info_str_and_start_bridges(
        num_nodes,plan_factory)

FLAPPING_NUM_NODES = 5
def produce_flapping_info_str_and_start_bridges():
    '''
    Always assume that using 5 nodes.
    '''
    connection_info_dict = _produce_connection_info_dict(FLAPPING_NUM_NODES)
    cohort_ids = connection_info_dict.keys()
    # FIXME: hardcoded flapping num nodes, etc.
    internally_connected_ids = cohort_ids[0:3]
    singly_connected_ids = cohort_ids[3:]

    singly_connected_pair_one = (
        singly_connected_ids[0],internally_connected_ids[0])
    singly_connected_pair_two = (
        singly_connected_ids[1],internally_connected_ids[1])

    # keys are local_cohort_id-s, values are plan_factory_dicts (keys
    # to plan factories)
    full_plan_factory_dict = {}
    for local_cohort_id in connection_info_dict:
        plan_factory_dict = {}
        full_plan_factory_dict[local_cohort_id] = plan_factory_dict
        
        for remote_cohort_id in connection_info_dict:
            if remote_cohort_id == local_cohort_id:
                continue

            if ((local_cohort_id in internally_connected_ids) and
                (remote_cohort_id in internally_connected_ids)):
                plan_factory_dict[remote_cohort_id] = PassThroughPlanFactory()

            elif ((local_cohort_id in singly_connected_pair_one) and
                  (remote_cohort_id in singly_connected_pair_one)):
                # either local or remote are not in centrally
                # connected group: check if they're singly connected
                plan_factory_dict[remote_cohort_id] = PassThroughPlanFactory()
            elif ((local_cohort_id in singly_connected_pair_two) and
                  (remote_cohort_id in singly_connected_pair_two)):
                # same as above elif condition
                plan_factory_dict[remote_cohort_id] = PassThroughPlanFactory()
            else:
                # not connected
                plan_factory_dict[remote_cohort_id] = DropPlanFactory()

    to_return = ''
    for local_cohort_id in connection_info_dict:
        local_cohort_plan_factory_dict = (
            full_plan_factory_dict[local_cohort_id])
        
        local_connection_info = connection_info_dict[local_cohort_id]
        local_connection_info.start_all_bridges_remote_specific(
            local_cohort_plan_factory_dict)
        to_return += local_connection_info.produce_java_arg_str() + "|" 

    return to_return
            

def _produce_connection_info_dict(num_nodes):
    '''
    @param {int} num_nodes
    @returns {dict} --- Keys are cohort ids.  Values are
    CohortConnectionInfo objects.
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
    return connection_info_dict


def _produce_connection_info_str_and_start_bridges(num_nodes,
                                                   plan_factory):

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
    connection_info_dict = _produce_connection_info_dict(num_nodes)
    
    to_return = ''
    for local_cohort_id in connection_info_dict:
        local_connection_info = connection_info_dict[local_cohort_id]
        local_connection_info.start_all_bridges(plan_factory)
        to_return += local_connection_info.produce_java_arg_str() + "|" 

    return to_return
