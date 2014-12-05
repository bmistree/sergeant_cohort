#!/usr/bin/env python

import sys
import json

import matplotlib.pyplot as plt
import matplotlib.lines as lines

class LineStyles(object):
    LEADER = '-'
    ELECTION = ':'
    FOLLOWER = '--'

class Colors(object):
    LEADER = 'red'
    ELECTION = 'green'
    FOLLOWER = 'blue'

class States(object):
    LEADER = 0
    ELECTION = 1
    FOLLOWER = 2
    
    
class HistoryElement(object):
    def __init__(self,node_id,timestamp,leader_id):
        self.timestamp = timestamp

        if leader_id == -1:
            self.linestyle = LineStyles.ELECTION
            self.color = Colors.ELECTION
            self.state = States.ELECTION
        elif leader_id == node_id:
            self.linestyle = LineStyles.LEADER
            self.color = Colors.LEADER
            self.state = States.LEADER
        else:
            self.linestyle = LineStyles.FOLLOWER
            self.color = Colors.FOLLOWER
            self.state = States.FOLLOWER

    def remove_timestamp_offset(self,offset):
        '''
        Used to remove offset from timestamps.
        '''
        self.timestamp -= offset

    def normalize_timestamp(self,max_timestamp):
        self.timestamp = float(self.timestamp)/float(max_timestamp)
        
        
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

    def max_timestamp(self):
        '''
        Returns the maximum timestamp of all history elements.
        '''
        return self.history[-1].timestamp

    def normalize_timestamps(self,max_timestamp):
        for history_element in self.history:
            history_element.normalize_timestamp(max_timestamp)
    
    def remove_timestamp_offset(self,offset):
        for history_element in self.history:
            history_element.remove_timestamp_offset(offset)

    def plot_history(self,ax,line_height):
        '''
        @param {matplotlib axes} ax
        @param {float} line_height --- 0 to 1.0
        '''
        # draw in election cycle to start
        draw_line(
            ax,0,line_height,self.history[0].timestamp,line_height,
            LineStyles.ELECTION,Colors.ELECTION)

        for i in range(1,len(self.history)):
            begin_element = self.history[i-1]
            start_point_x = begin_element.timestamp
            style = begin_element.linestyle
            color = begin_element.color
            
            end_element = self.history[i]
            end_point_x = end_element.timestamp
            
            draw_line(
                ax,start_point_x,line_height,end_point_x,line_height,style,
                color)
                      
    def plot_follower_election_only(self,ax, election_height, follower_height):
        '''
        Should be used on node that can never be leader.
        '''
        style = '-'
        color = 'cornflowerblue'
        
        # draw in election cycle to start
        draw_line(
            ax,0,election_height,self.history[0].timestamp,election_height,
            style,color)

        for i in range(1,len(self.history)):
            begin_element = self.history[i-1]
            start_point_x = begin_element.timestamp

            if begin_element.state == States.ELECTION:
                line_height = election_height
            elif begin_element.state == States.FOLLOWER:
                line_height = follower_height
            else:
                print (
                    '\nShould only use this method for ' +
                    'non-leader node')
                assert False

            
            end_element = self.history[i]
            end_point_x = end_element.timestamp

            draw_line(
                ax,start_point_x,line_height,end_point_x,line_height,style,
                color)
        
        
            
        
def draw_line(ax,start_point_x, start_point_y, end_point_x, end_point_y,style,color):
    '''
    x and y points range from 0 to 1
    '''
    line = [(start_point_x,start_point_y), (end_point_x,end_point_y)]
    (line_xs, line_ys) = zip(*line)
    ax.add_line(
        lines.Line2D(
            line_xs, line_ys, linewidth=4, color=color,
            linestyle=style))

    
def run(input_json_filename,output_filename):

    all_data = []

    with open(input_json_filename) as fd:
        json_data = json.load(fd)
        for single_node_json_data in json_data:            
            all_data.append(SingleNodeHistory(single_node_json_data))
        
    # remove timestamp offset so all times start ~ 0
    min_timestamp = None
    max_timestamp = None
    for single_node_history in all_data:
        if min_timestamp is None:
            min_timestamp = single_node_history.min_timestamp()
            max_timestamp = single_node_history.max_timestamp()
        else:
            min_timestamp = min(
                min_timestamp,single_node_history.min_timestamp())
            max_timestamp = max(
                max_timestamp,single_node_history.max_timestamp())

    max_timestamp -= min_timestamp
    for single_node_history in all_data:
        single_node_history.remove_timestamp_offset(min_timestamp)
        single_node_history.normalize_timestamps(max_timestamp)
        
    # now actually draw graphs
    figure, axes = plt.subplots()

    # draw state of all nodes
    # for i in range(0,len(all_data)):
    #     line_height = float(i)/float(len(all_data)) + .2
    #     single_node_history = all_data[i]
    #     single_node_history.plot_history(axes,line_height)


    # draw graph just for the single follower
    all_data[0].plot_follower_election_only(axes, .2,.8)
    plt.show()
        

        
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
