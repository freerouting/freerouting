#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <ctime>
std::vector<std::string> splitArray;
void split(std::string str, const char split){
	std::istringstream iss(str);
	std::string token;
	while(std::getline(iss, token, split)){
		splitArray.push_back(token);
	}
}
int main(){
    int times = 0, clocks = 0;
    std::string line;
    while(std::getline(std::cin, line)){
        split(line, ' ');
        // for(auto it = splitArray.begin(); it != splitArray.end(); it++){
        //     std::clog << '(' << it - splitArray.begin() << " \"" << *it << "\"" << ") ";
        // }
        // std::clog << std::endl;
        if(splitArray[3] == "INFO" && splitArray[5] == "Starting"
            && splitArray[6] == "auto-routing..."){
            clocks = clock();
            fprintf(stderr, "{\"status\": \"startRoute\"}\n");
        }else if(splitArray[3] == "INFO" && splitArray[5] == "Auto-routing"
            && splitArray[6] == "was" && splitArray[7] == "completed"){
            fprintf(stderr, "{\"status\": \"routingResult\"}");
        }else if(splitArray[3] == "INFO" && splitArray[5] == "Starting"
            && splitArray[6] == "route" && splitArray[7] == "optimization"){
            fprintf(stderr, "{\"status\": \"startOptimize\"}\n");
        }else if(splitArray[3] == "INFO" && splitArray[5] == "Route"
            && splitArray[6] == "optimization" && splitArray[7] == "was"
            && splitArray[8] == "completed"){
            fprintf(stderr, "{\"status\": \"optimizeResult\"}\n");
        }
        if(splitArray[3] == "INFO" && splitArray[5] == "Saving"){
            times++;
            printf("%s %s [Thread-0] INFO Auto-routing progress #%d completed in %lf seconds.",
                splitArray[0].c_str(), splitArray[1].c_str(), times, (clock() - clocks) / (double)CLOCKS_PER_SEC);
            clocks = clock();
            fprintf(stderr, "{\"status\": \"routingProgress\"}");
        }
        puts(line.c_str());
        splitArray.clear();
    }
    return 0;
}