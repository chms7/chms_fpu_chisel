VERILATOR = verilator

SIM_DIR = ~/Workbench/riscv/rvf/MyFPU/sim
GENERATE_DIR = $(SIM_DIR)/generated
RTL_V = $(GENERATE_DIR)/*.v
SIM_SRC = sim_main
SIM_CPP = $(SIM_DIR)/$(SIM_SRC).cpp

export PATH := $(PATH):$(abspath ./utils)

test:
	mill -i __.test

verilog:
	mkdir -p $(GENERATE_DIR)
	mill -i __.test.runMain Elaborate -td $(GENERATE_DIR)

help:
	mill -i __.test.runMain Elaborate --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

sim:
	cd $(SIM_DIR) && \
	$(VERILATOR) --cc --exe --build -j 8 --trace $(SIM_CPP) $(RTL_V) && \
	bash -c $(SIM_DIR)/obj_dir/V[^ABC]* && \
	cd - && \
	gtkwave $(SIM_DIR)/obj_dir/wave.vcd

clean:
	-rm -rf $(GENERATE_DIR) && \
	rm -rf $(SIM_DIR)/obj_dir

.PHONY: test verilog help compile bsp reformat checkformat sim clean
