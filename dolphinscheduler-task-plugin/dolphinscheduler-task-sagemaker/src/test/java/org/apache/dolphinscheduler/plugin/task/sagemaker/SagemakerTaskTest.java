/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.plugin.task.sagemaker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;

import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.plugin.datasource.api.utils.DataSourceUtils;
import org.apache.dolphinscheduler.plugin.datasource.sagemaker.param.SagemakerConnectionParam;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ResourceParametersHelper;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sagemaker.AmazonSageMaker;
import com.amazonaws.services.sagemaker.AmazonSageMakerClientBuilder;
import com.amazonaws.services.sagemaker.model.DescribePipelineExecutionResult;
import com.amazonaws.services.sagemaker.model.StartPipelineExecutionRequest;
import com.amazonaws.services.sagemaker.model.StartPipelineExecutionResult;
import com.amazonaws.services.sagemaker.model.StopPipelineExecutionResult;

@ExtendWith(MockitoExtension.class)
public class SagemakerTaskTest {

    private final String pipelineExecutionArn = "test-pipeline-arn";
    private final String clientRequestToken = "test-pipeline-token";
    private SagemakerTask sagemakerTask;
    private AmazonSageMaker client;
    private PipelineUtils pipelineUtils = new PipelineUtils();

    private static final String MOCK_USERNAME = "lucky";
    private static final String MOCK_PASSWORD = "root";
    private static final String MOCK_TYPE = "SAGEMAKER";
    private static final String MOCK_AWS_REGION = "REGION";

    private static MockedStatic<DataSourceUtils> dataSourceUtilsStaticMock = null;
    private static MockedStatic<AmazonSageMakerClientBuilder> sageMakerMockedStatic = null;

    @BeforeEach
    public void before() {
        String parameters = buildParameters();
        System.out.println(parameters);
        TaskExecutionContext taskExecutionContext = Mockito.mock(TaskExecutionContext.class);
        ResourceParametersHelper resourceParametersHelper = Mockito.mock(ResourceParametersHelper.class);
        SagemakerConnectionParam sagemakerConnectionParam = Mockito.mock(SagemakerConnectionParam.class);
        Mockito.when(taskExecutionContext.getTaskParams()).thenReturn(parameters);
        Mockito.when(taskExecutionContext.getResourceParametersHelper()).thenReturn(resourceParametersHelper);

        dataSourceUtilsStaticMock = Mockito.mockStatic(DataSourceUtils.class);
        dataSourceUtilsStaticMock.when(() -> DataSourceUtils.buildConnectionParams(Mockito.any(), Mockito.any()))
                .thenReturn(sagemakerConnectionParam);

        client = Mockito.mock(AmazonSageMaker.class);
        BasicAWSCredentials basicAWSCredentials = Mockito.mock(BasicAWSCredentials.class);
        AWSCredentialsProvider awsCredentialsProvider = Mockito.mock(AWSCredentialsProvider.class);
        Mockito.when(awsCredentialsProvider.getCredentials()).thenReturn(basicAWSCredentials);

        sageMakerMockedStatic = Mockito.mockStatic(AmazonSageMakerClientBuilder.class);

        sageMakerMockedStatic
                .when(() -> AmazonSageMakerClientBuilder.standard().withCredentials(awsCredentialsProvider)
                        .withRegion((Regions) any()).build())
                .thenReturn(client);

        sagemakerTask = spy(new SagemakerTask(taskExecutionContext));
        sagemakerTask.init();

        StartPipelineExecutionResult startPipelineExecutionResult = Mockito.mock(StartPipelineExecutionResult.class);
        Mockito.lenient().when(startPipelineExecutionResult.getPipelineExecutionArn()).thenReturn(pipelineExecutionArn);

        StopPipelineExecutionResult stopPipelineExecutionResult = Mockito.mock(StopPipelineExecutionResult.class);
        Mockito.lenient().when(stopPipelineExecutionResult.getPipelineExecutionArn()).thenReturn(pipelineExecutionArn);

        DescribePipelineExecutionResult describePipelineExecutionResult =
                Mockito.mock(DescribePipelineExecutionResult.class);
        Mockito.lenient().when(describePipelineExecutionResult.getPipelineExecutionStatus()).thenReturn("Executing",
                "Succeeded");

        Mockito.lenient().when(client.startPipelineExecution(any())).thenReturn(startPipelineExecutionResult);
        Mockito.lenient().when(client.stopPipelineExecution(any())).thenReturn(stopPipelineExecutionResult);
        Mockito.lenient().when(client.describePipelineExecution(any())).thenReturn(describePipelineExecutionResult);
    }

    @AfterEach
    public void afterEach() {
        dataSourceUtilsStaticMock.close();
        sageMakerMockedStatic.close();
    }

    @Test
    public void testStartPipelineRequest() throws Exception {
        StartPipelineExecutionRequest request = sagemakerTask.createStartPipelineRequest();
        Assertions.assertEquals("AbalonePipeline", request.getPipelineName());
        Assertions.assertEquals("test Pipeline", request.getPipelineExecutionDescription());
        Assertions.assertEquals("AbalonePipeline", request.getPipelineExecutionDisplayName());
        Assertions.assertEquals("AbalonePipeline", request.getPipelineName());
        Assertions.assertEquals(Integer.valueOf(1),
                request.getParallelismConfiguration().getMaxParallelExecutionSteps());
    }

    @Test
    public void testPipelineExecution() throws Exception {
        PipelineUtils.PipelineId pipelineId =
                pipelineUtils.startPipelineExecution(client, sagemakerTask.createStartPipelineRequest());
        Assertions.assertEquals(pipelineExecutionArn, pipelineId.getPipelineExecutionArn());
        Assertions.assertEquals(0, pipelineUtils.checkPipelineExecutionStatus(client, pipelineId));
        pipelineUtils.stopPipelineExecution(client, pipelineId);
    }

    private String buildParameters() {
        SagemakerParameters parameters = new SagemakerParameters();
        String sagemakerRequestJson;
        try (InputStream i = this.getClass().getResourceAsStream("SagemakerRequestJson.json")) {
            assert i != null;
            sagemakerRequestJson = IOUtils.toString(i, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        parameters.setSagemakerRequestJson(sagemakerRequestJson);
        parameters.setUsername(MOCK_USERNAME);
        parameters.setPassword(MOCK_PASSWORD);
        parameters.setAwsRegion(MOCK_AWS_REGION);
        parameters.setType(MOCK_TYPE);

        return JSONUtils.toJsonString(parameters);
    }
}
