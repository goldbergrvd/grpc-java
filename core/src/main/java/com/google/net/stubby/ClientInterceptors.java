/*
 * Copyright 2014, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.net.stubby;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class for {@link ClientInterceptor}s
 */
public class ClientInterceptors {

  // Prevent instantiation
  private ClientInterceptors() {}

  /**
   * Create a new {@code Channel} that will call {@code interceptors} before starting an RPC on the
   * given channel.
   */
  public static Channel intercept(Channel channel, ClientInterceptor... interceptors) {
    return intercept(channel, Arrays.asList(interceptors));
  }

  /**
   * Create a new {@code Channel} that will call {@code interceptors} before starting an RPC on the
   * given channel.
   */
  public static Channel intercept(Channel channel, List<ClientInterceptor> interceptors) {
    Preconditions.checkNotNull(channel);
    if (interceptors.isEmpty()) {
      return channel;
    }
    return new InterceptorChannel(channel, interceptors);
  }

  private static class InterceptorChannel implements Channel {
    private final Channel channel;
    private final Iterable<ClientInterceptor> interceptors;

    private InterceptorChannel(Channel channel, List<ClientInterceptor> interceptors) {
      this.channel = channel;
      this.interceptors = ImmutableList.copyOf(interceptors);
    }

    @Override
    public <ReqT, RespT> Call<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method) {
      return new ProcessInterceptorChannel(channel, interceptors).newCall(method);
    }
  }

  private static class ProcessInterceptorChannel implements Channel {
    private final Channel channel;
    private Iterator<ClientInterceptor> interceptors;

    private ProcessInterceptorChannel(Channel channel, Iterable<ClientInterceptor> interceptors) {
      this.channel = channel;
      this.interceptors = interceptors.iterator();
    }

    @Override
    public <ReqT, RespT> Call<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method) {
      if (interceptors != null && interceptors.hasNext()) {
        return interceptors.next().interceptCall(method, this);
      } else {
        Preconditions.checkState(interceptors != null,
            "The channel has already been called. "
            + "Some interceptor must have called on \"next\" twice.");
        interceptors = null;
        return channel.newCall(method);
      }
    }
  }

  /**
   * A {@link Call} which forwards all of it's methods to another {@link Call}.
   */
  public static class ForwardingCall<ReqT, RespT> extends Call<ReqT, RespT> {

    private final Call<ReqT, RespT> delegate;

    public ForwardingCall(Call<ReqT, RespT> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata.Headers headers) {
      this.delegate.start(responseListener, headers);
    }

    @Override
    public void cancel() {
      this.delegate.cancel();
    }

    @Override
    public void halfClose() {
      this.delegate.halfClose();
    }

    @Override
    public void sendPayload(ReqT payload) {
      this.delegate.sendPayload(payload);
    }
  }

  /**
   * A {@link com.google.net.stubby.Call.Listener} which forwards all of its methods to another
   * {@link com.google.net.stubby.Call.Listener}.
   */
  public static class ForwardingListener<T> extends Call.Listener<T> {

    private final Call.Listener<T> delegate;

    public ForwardingListener(Call.Listener<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Void> onHeaders(Metadata.Headers headers) {
      return delegate.onHeaders(headers);
    }

    @Override
    public ListenableFuture<Void> onPayload(T payload) {
      return delegate.onPayload(payload);
    }

    @Override
    public void onClose(Status status, Metadata.Trailers trailers) {
      delegate.onClose(status, trailers);
    }
  }
}