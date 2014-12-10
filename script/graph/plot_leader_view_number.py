#!/usr/bin/env python

import sys
import json

import matplotlib.pyplot as plt
import matplotlib.lines as lines

NO_LEADER_ID = -1



class LeaderHistoryElement(object):
    LEADER_ID_GRAPH_HEIGHTS = {
        0: .025,
        1: .25,
        2: .53,
        NO_LEADER_ID: .8
        }
    X_LEFT_OFFSET = .03

    def __init__(self,view_number,leader_id):
        self.view_number = view_number
        self.leader_id = leader_id
    
    def _x_position_from_view_number(self,max_view_number):
        return (
            (float(self.view_number) / float(max_view_number)) -
            LeaderHistoryElement.X_LEFT_OFFSET)

    def _y_position_from_leader_id(self):
        return LeaderHistoryElement.LEADER_ID_GRAPH_HEIGHTS[self.leader_id]
    
    def draw_on_axes(self,axes,max_view_number):
        position_x = self._x_position_from_view_number(max_view_number)
        position_y = self._y_position_from_leader_id()
        axes.annotate('X',xy=(position_x,position_y))
        
def draw_leader_history_graph(leader_history_list, output_filename,
                              title):
    '''
    @param {list} leader_history_list --- Each element is a
    LeaderHistoryElement.
    '''
    max_leader_history_element = leader_history_list[-1]
    max_view_number = max_leader_history_element.view_number
    
    # now actually draw graphs
    figure, axes = plt.subplots()

    figure.set_figheight(3)
    figure.set_figwidth(6)
    figure.subplots_adjust(top=.90,bottom=.10,left=.02,right=.98)
    for leader_history_element in leader_history_list:
        leader_history_element.draw_on_axes(axes,max_view_number)
        
    label_x = .05
    label_offset_y = .07
    no_leader_y = (
        LeaderHistoryElement.LEADER_ID_GRAPH_HEIGHTS[NO_LEADER_ID] +
        label_offset_y)
    axes.annotate('No leader',xy=(label_x,no_leader_y))
    a_leader_y = (
        LeaderHistoryElement.LEADER_ID_GRAPH_HEIGHTS[0] +
        label_offset_y)
    axes.annotate('Node A leader',xy=(label_x,a_leader_y))
    b_leader_y = (
        LeaderHistoryElement.LEADER_ID_GRAPH_HEIGHTS[1] +
        label_offset_y)
    axes.annotate('Node B leader',xy=(label_x,b_leader_y))
    c_leader_y = (
        LeaderHistoryElement.LEADER_ID_GRAPH_HEIGHTS[2] +
        label_offset_y)
    axes.annotate('Node C leader',xy=(label_x,c_leader_y))

    axes.set_title(title)
    axes.set_xticks([])
    axes.set_yticks([])        
    axes.set_xlabel('Normalized view number')
    plt.savefig(output_filename)    

def run(input_json_filename,output_filename,title):
    '''
    @param {string} input_json_filename --- The name of a file
    containing a json list.  Each element of the json list has the
    following format:
        {"view_number": <int>,"leader_id": <int}

    @param {string} output_filename --- The name of the file to save
    to.
    '''

    # each element is a LeaderHistoryElement.  Ordered in order of
    # view number.
    leader_history_list = []
    with open(input_json_filename) as fd:
        json_data = json.load(fd)
        for leader_history_data in json_data:
            leader_history_element = LeaderHistoryElement(
                leader_history_data['view_number'],
                leader_history_data['leader_id'])
            leader_history_list.append(leader_history_element)
    

    # fill in missing view numbers with no leader ids.
    filled_leader_history_list = []
    prev_element = None
    for i in range(0,len(leader_history_list)):
        current_element = leader_history_list[i]
        if i != 0:
            prev_element = leader_history_list[i-1]
            missing_view_numbers = range(
                prev_element.view_number+1,current_element.view_number)
            # fill in leaderless entries, designating them with no
            # leader
            for missing_view_number in missing_view_numbers:
                filled_leader_history_list.append(
                    LeaderHistoryElement(missing_view_number,NO_LEADER_ID))

        filled_leader_history_list.append(current_element)            

        # include missing view numbers from front
        for i in reversed(range(1,filled_leader_history_list[0].view_number)):
            no_leader_element = LeaderHistoryElement(i,NO_LEADER_ID)
            filled_leader_history_list.insert(0,no_leader_element)
        

    # now actually draw the graph with filled leader_history
    draw_leader_history_graph(
        filled_leader_history_list, output_filename,title)


if __name__ == '__main__':
    input_json_filename = sys.argv[1]
    output_filename = sys.argv[2]
    title = sys.argv[3]
    run(input_json_filename,output_filename,title)
