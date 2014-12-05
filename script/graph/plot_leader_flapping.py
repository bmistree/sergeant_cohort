#!/usr/bin/env python

import sys
import json

import matplotlib.pyplot as plt

class LineStyles(object):
    LEADER = '-'
    ELECTION = ':'
    FOLLOWER = '--'

    
class HistoryElement(object):
    def __init__(self,node_id,timestamp,leader_id):
        self.timestamp = timestamp

        if leader_id == -1:
            self.linestyle = LineStyles.ELECTION
        elif leader_id == node_id:
            self.linestyle = LineStyles.LEADER
        else:
            self.linestyle = LineStyles.FOLLOWER

    def remove_timestamp_offset(self,offset):
        '''
        Used to remove offset from timestamps.
        '''
        self.timestamp -= offset

        
class SingleNodeHistory(object):
    def __init__(self,node_history_dict):
        '''
        @param {dict} node_history_dict --- Has the form:

        {
            cohort_id: <number>,
            history: [ <history element>, <history_element>, ...]
        }
        
        where history_element has the form:
        {
            timestamp: <number>,
            view_number: <number>,
            leader_id: <number> ... can be -1 if entered election state.
        }
        '''
        self.cohort_id = node_history_dict['cohort_id']

        # composed of HistoryElement objects
        self.history = []
        for history_json in node_history_dict['history']:
            timestamp = history_json['timestamp']
            leader_id = history_json['leader_id']
            self.history.append(
                HistoryElement(self.cohort_id,timestamp,leader_id))
        
    def min_timestamp(self):
        '''
        Returns the minimum timestamp of all history elements.
        '''
        return self.history[0].timestamp

    def remove_timestamp_offset(self,offset):
        for history_element in self.history:
            history_element.remove_timestamp_offset(offset)

    
    
def run(input_json_filename,output_filename):

    all_data = []

    with open(input_json_filename) as fd:
        json_data = json.load(fd)
        for single_node_json_data in json_data:
            all_data.append(SingleNodeHistory(single_node_json_data))
        
    
    
    # # Plot all line styles.
    # figure, axes = plt.subplots()
    # for y, linestyle in enumerate(linestyles):
    #     ax.text(-0.5, y, nice_repr(linestyle), **text_style)
    #     ax.plot(y * points, linestyle=linestyle, color=color, linewidth=3)
    #     format_axes(ax)
    #     ax.set_title('line styles')

    # plt.show()
    


if __name__ == '__main__':
    input_json_filename = sys.argv[1]
    output_filename = sys.argv[2]
    run(input_json_filename,output_filename)
