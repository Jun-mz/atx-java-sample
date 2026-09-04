package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.Json;
import com.example.was.aws.InstanceMetadataClient;

/**
 * GET /instance — 이 요청을 처리한 EC2 인스턴스 정보. 장애 분석 시 어느 노드에
 * 붙었는지 확인하는 용도로 쓴다. 값은 IMDS 에서 직접 가져온다.
 */
public final class InstanceInfoHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        response.json("{\"instanceId\":\"" + Json.escape(InstanceMetadataClient.instanceId())
                + "\",\"availabilityZone\":\"" + Json.escape(InstanceMetadataClient.availabilityZone())
                + "\",\"privateIp\":\"" + Json.escape(InstanceMetadataClient.privateIpv4())
                + "\",\"iamRole\":\"" + Json.escape(InstanceMetadataClient.iamRoleName()) + "\"}");
    }
}
