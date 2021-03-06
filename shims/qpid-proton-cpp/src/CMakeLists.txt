#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

project (qpid-interop-test-cpp-shim)

cmake_minimum_required(VERSION 2.8.7 FATAL_ERROR)

message("PROTON_INSTALL_DIR=${PROTON_INSTALL_DIR}")
include_directories(${PROTON_INSTALL_DIR}/include)
include_directories(/usr/include/jsoncpp)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
link_directories(${PROTON_INSTALL_DIR}/lib64)
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
set(CPP_SHIM_INSTALL_ROOT "${CMAKE_INSTALL_PREFIX}/libexec/qpid_interop_test/shims/qpid-proton-cpp")



# === FUNCTION addAmqpTest ===

function(addAmqpTest testName)
# --- Sender ---

set(${testName}_Sender_SOURCES
    qpidit/${testName}/Sender.hpp
    qpidit/${testName}/Sender.cpp
)

add_executable(${testName}_Sender ${${testName}_Sender_SOURCES})
target_link_libraries(${testName}_Sender Common Common_Amqp ${Common_Link_LIBS})
set_target_properties(${testName}_Sender PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/${testName}"
    OUTPUT_NAME Sender
)

install(PROGRAMS "${CMAKE_CURRENT_BINARY_DIR}/${testName}/Sender"
        DESTINATION "${CPP_SHIM_INSTALL_ROOT}/${testName}")

# --- Receiver ---

set(${testName}_Receiver_SOURCES
    qpidit/${testName}/Receiver.hpp
    qpidit/${testName}/Receiver.cpp
)

add_executable(${testName}_Receiver ${${testName}_Receiver_SOURCES})
target_link_libraries(${testName}_Receiver Common Common_Amqp ${Common_Link_LIBS})
set_target_properties(${testName}_Receiver PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/${testName}"
    OUTPUT_NAME Receiver
)

install(PROGRAMS "${CMAKE_CURRENT_BINARY_DIR}/${testName}/Receiver"
        DESTINATION "${CPP_SHIM_INSTALL_ROOT}/${testName}")

endfunction(addAmqpTest testName)



# === FUNCTION addJmsTest ===

function(addJmsTest testName)
# --- Sender ---

set(${testName}_Sender_SOURCES
    qpidit/${testName}/Sender.hpp
    qpidit/${testName}/Sender.cpp
)

add_executable(${testName}_Sender ${${testName}_Sender_SOURCES})
target_link_libraries(${testName}_Sender Common Common_Jms ${Common_Link_LIBS})
set_target_properties(${testName}_Sender PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/${testName}"
    OUTPUT_NAME Sender
)

install(PROGRAMS "${CMAKE_CURRENT_BINARY_DIR}/${testName}/Sender"
        DESTINATION "${CPP_SHIM_INSTALL_ROOT}/${testName}")

# --- Receiver ---

set(${testName}_Receiver_SOURCES
    qpidit/${testName}/Receiver.hpp
    qpidit/${testName}/Receiver.cpp
)

add_executable(${testName}_Receiver ${${testName}_Receiver_SOURCES})
target_link_libraries(${testName}_Receiver Common Common_Jms ${Common_Link_LIBS})
set_target_properties(${testName}_Receiver PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/${testName}"
    OUTPUT_NAME Receiver
)

install(PROGRAMS "${CMAKE_CURRENT_BINARY_DIR}/${testName}/Receiver"
        DESTINATION "${CPP_SHIM_INSTALL_ROOT}/${testName}")

endfunction(addJmsTest testName)


# --- Common files and libs ---

set(Common_SOURCES
    qpidit/QpidItErrors.hpp
    qpidit/QpidItErrors.cpp
)
add_library(Common ${Common_SOURCES})

set(Common_Amqp_SOURCES
    qpidit/AmqpTestBase.hpp
    qpidit/AmqpTestBase.cpp
    qpidit/AmqpReceiverBase.hpp
    qpidit/AmqpReceiverBase.cpp
    qpidit/AmqpSenderBase.hpp
    qpidit/AmqpSenderBase.cpp
)
add_library(Common_Amqp ${Common_Amqp_SOURCES})

set(Common_Jms_SOURCES
    qpidit/JmsTestBase.hpp
    qpidit/JmsTestBase.cpp
)
add_library(Common_Jms ${Common_Jms_SOURCES})

set(Common_Link_LIBS
    qpid-proton-cpp
    jsoncpp
)


addAmqpTest(amqp_types_test)
addAmqpTest(amqp_large_content_test)
addJmsTest(jms_messages_test)
addJmsTest(jms_hdrs_props_test)
