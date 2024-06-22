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
    while(true){
        std::getline(std::cin, line);
        split(line, ' ');
        if(splitArray.size() == 7 && splitArray[2] == "[Thread-0]" 
            && splitArray[3] == "INFO" && splitArray[4] == "Starting"
            && splitArray[5] == "auto-routing..."){
            clocks = clock();
            fprintf(stderr, "{\"status\": \"startRoute\"}\n");
        }else if(splitArray.size() == 12 && splitArray[2] == "[Thread-0]" 
            && splitArray[3] == "INFO" && splitArray[4] == "Auto-routing"
            && splitArray[5] == "was" && splitArray[6] == "completed"){
            fprintf(stderr, "{\"status\": \"routingResult\", \"times\": %d}\n", times);
        }else if(splitArray.size() == 10 && splitArray[2] == "[Thread-0]" 
            && splitArray[3] == "INFO" && splitArray[4] == "Starting"
            && splitArray[5] == "route" && splitArray[6] == "optimization"){
            fprintf(stderr, "{\"status\": \"startOptimize\"}\n");
        }else if(splitArray.size() == 11 && splitArray[2] == "[Thread-0]"
            && splitArray[3] == "INFO" && splitArray[4] == "Route"
            && splitArray[5] == "optimization" && splitArray[6] == "was"
            && splitArray[7] == "completed"){
            fprintf(stderr, "{\"status\": \"optimizeResult\"}\n");
        }
        if(splitArray.size() == 6 && splitArray[2] == "[Thread-0]" 
            && splitArray[3] == "INFO" && splitArray[4] == "Saving"){
            times++;
            printf("%s %s [Thread-0] INFO Auto-routing progress #%d completed in %lf seconds.",
                splitArray[0].c_str(), splitArray[1].c_str(), times, (clock() - clocks) / (double)CLOCKS_PER_SEC);
            clocks = clock();
            fprintf(stderr, "{\"status\": \"routingProgress\", \"times\": %d}\n", times);
        }else puts(line.c_str());
    }
    return 0;
}