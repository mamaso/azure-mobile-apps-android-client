/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */

/**
 * MobileServiceConnection.java
 */
package com.microsoft.windowsazure.mobileservices.http;

import android.os.Build;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.microsoft.windowsazure.mobileservices.MobileServiceApplication;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceException;
import com.microsoft.windowsazure.mobileservices.ServiceFilterResponseCallback;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;

/**
 * Class for handling communication with Microsoft Azure Mobile Services REST APIs
 */
public class MobileServiceConnection {

    /**
     * Header value to represent JSON content-type
     */
    public static final String JSON_CONTENTTYPE = "application/json";

    /**
     * Request header to indicate the API Version
     */
    private static final String ZUMO_API_VERSION_HEADER = "ZUMO-API-VERSION";

    /**
     * Request header to indicate the Mobile Service Installation ID
     */
    private static final String X_ZUMO_INSTALLATION_ID_HEADER = "X-ZUMO-INSTALLATION-ID";

    /**
     * Request header to indicate the Mobile Service user authentication token
     */
    private static final String X_ZUMO_AUTH_HEADER = "X-ZUMO-AUTH";

    /**
     * Name of the zumo version header.
     */
    private static final String X_ZUMO_VERSION_HEADER = "X-ZUMO-VERSION";

    /**
     * Header value to represent GZIP content-encoding
     */
    private static final String GZIP_CONTENTENCODING = "gzip";
    /**
     * Current SDK version
     */
    private static final String SDK_VERSION = "3.0.0";
    /**
     * The MobileServiceClient used for communication with the Mobile Service
     */
    private MobileServiceClient mClient;

    /**
     * Constructor for the MobileServiceConnection
     *
     * @param client The client used for communication with the Mobile Service
     */
    public MobileServiceConnection(MobileServiceClient client) {
        mClient = client;
    }

    /**
     * Generates the User-Agent
     */
    static String getUserAgent() {
        String userAgent = String.format("ZUMO/1.0 (lang=%s; os=%s; os_version=%s; arch=%s; version=%s)", "Java", "Android", Build.VERSION.RELEASE,
                Build.CPU_ABI, SDK_VERSION);

        return userAgent;
    }

    /**
     * Execute a request-response operation with a Mobile Service
     *
     * @param request          The request to execute
     * @param responseCallback Callback to invoke after the request is executed
     */
    public ListenableFuture<ServiceFilterResponse> start(final ServiceFilterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request can not be null");
        }

        ServiceFilter filter = mClient.getServiceFilter();
        // Set the request's headers
        configureHeadersOnRequest(request);
        return filter.handleRequest(request, new NextServiceFilterCallback() {

            @Override
            public ListenableFuture<ServiceFilterResponse> onNext(ServiceFilterRequest request) {
                SettableFuture<ServiceFilterResponse> future = SettableFuture.create();
                ServiceFilterResponse response = null;

                try {
                    response = request.execute();
                    int statusCode = response.getStatus().code;

                    // If the response has error throw exception
                    if (statusCode < 200 || statusCode >= 300) {
                        String responseContent = response.getContent();
                        if (responseContent != null && !responseContent.trim().equals("")) {
                            throw new MobileServiceException(responseContent, response);
                        } else {
                            throw new MobileServiceException(String.format("{'code': %d}", statusCode), response);
                        }
                    }

                    future.set(response);
                } catch (MobileServiceException e) {
                    future.setException(e);
                } catch (Exception e) {
                    future.setException(new MobileServiceException("Error while processing request.", e, response));
                }

                return future;
            }
        });
    }

    /**
     * Execute a request-response operation with a Mobile Service
     *
     * @param request          The request to execute
     * @param responseCallback Callback to invoke after the request is executed
     */
    public void start(final ServiceFilterRequest request, final ServiceFilterResponseCallback responseCallback) {
        ListenableFuture<ServiceFilterResponse> startFuture = start(request);

        Futures.addCallback(startFuture, new FutureCallback<ServiceFilterResponse>() {
            @Override
            public void onFailure(Throwable exception) {
                if (exception instanceof Exception) {
                    responseCallback.onResponse(MobileServiceException.getServiceResponse(exception), (Exception) exception);
                } else {
                    responseCallback.onResponse(MobileServiceException.getServiceResponse(exception), new Exception(exception));
                }
            }

            @Override
            public void onSuccess(ServiceFilterResponse response) {
                responseCallback.onResponse(response, null);
            }
        });
    }

    /**
     * Configures the HttpRequestBase to execute a request with a Mobile Service
     *
     * @param request The request to configure
     */
    private void configureHeadersOnRequest(ServiceFilterRequest request) {
        // Add the authentication header if the user is logged in
        MobileServiceUser user = mClient.getCurrentUser();
        if (user != null && user.getAuthenticationToken() != "") {
            request.addHeader(X_ZUMO_AUTH_HEADER, user.getAuthenticationToken());
        }

        if (SDK_VERSION != null) {
            request.addHeader(X_ZUMO_VERSION_HEADER, SDK_VERSION);
        }

        // Set the User Agent header
        request.addHeader("User-Agent", getUserAgent());

        // Set the Mobile Service SDK Version
        request.addHeader(ZUMO_API_VERSION_HEADER, "2.0.0");

        // Set the special Installation ID header
        request.addHeader(X_ZUMO_INSTALLATION_ID_HEADER, MobileServiceApplication.getInstallationId(mClient.getContext()));

        if (!requestContainsHeader(request, "Accept")) {
            request.addHeader("Accept", JSON_CONTENTTYPE);
        }

        if (!requestContainsHeader(request, "Accept-Encoding")) {
            request.addHeader("Accept-Encoding", GZIP_CONTENTENCODING);
        }
    }

    /**
     * Verifies if the request contains the specified header
     *
     * @param request    The request to verify
     * @param headerName The header name to find
     * @return True if the header is present, false otherwise
     */
    private boolean requestContainsHeader(ServiceFilterRequest request, String headerName) {

        String value = request.getHeaders().get(headerName);

        return (value != null);
    }
}
