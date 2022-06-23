/*
  Copyright 2021 The Vine Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.bynix.vine.app.graal;

import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;

public final class EmptyByteBufStub {
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocateDirect(0);
    private static final long EMPTY_BYTE_BUFFER_ADDRESS;

    static {
        long emptyByteBufferAddress = 0;
        try {
            if (PlatformDependent.hasUnsafe()) {
                emptyByteBufferAddress = PlatformDependent.directBufferAddress(EMPTY_BYTE_BUFFER);
            }
        } catch (Throwable t) {
            // Ignore
        }
        EMPTY_BYTE_BUFFER_ADDRESS = emptyByteBufferAddress;
    }

    public static ByteBuffer emptyByteBuffer() {
        return EMPTY_BYTE_BUFFER;
    }

    public static long emptyByteBufferAddress() {
        return EMPTY_BYTE_BUFFER_ADDRESS;
    }

    private EmptyByteBufStub() {
    }
}
