/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import no.nordicsemi.android.ble.callback.DataSentCallback;
import no.nordicsemi.android.ble.callback.FailCallback;
import no.nordicsemi.android.ble.callback.SuccessCallback;
import no.nordicsemi.android.ble.callback.WriteProgressCallback;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.data.DataSplitter;
import no.nordicsemi.android.ble.data.DefaultMtuSplitter;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class WriteRequest extends ValueRequest<DataSentCallback> {
	private final static DataSplitter MTU_SPLITTER = new DefaultMtuSplitter();

	private WriteProgressCallback progressCallback;
	private DataSplitter dataSplitter;
	private final byte[] data;
	private final int writeType;
	private int count = 0;
	private boolean complete = false;

	WriteRequest(final @NonNull Type type) {
		this(type, null);
	}

	WriteRequest(final @NonNull Type type, final @Nullable BluetoothGattCharacteristic characteristic) {
		super(type, characteristic);
		// not used:
		this.data = null;
		this.writeType = 0;
		// getData(int) isn't called on enabling and disabling notifications/indications.
		this.complete = true;
	}

	WriteRequest(final @NonNull Type type, final @Nullable BluetoothGattCharacteristic characteristic,
				 final @Nullable byte[] data, final int offset, final int length, final int writeType) {
		super(type, characteristic);
		this.data = copy(data, offset, length);
		this.writeType = writeType;
	}

	WriteRequest(final @NonNull Type type, final @Nullable BluetoothGattDescriptor descriptor,
				 final @Nullable byte[] data, final int offset, final int length) {
		super(type, descriptor);
		this.data = copy(data, offset, length);
		this.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
	}

	private static byte[] copy(final @Nullable byte[] value, final int offset, final int length) {
		if (value == null || offset > value.length)
			return null;
		final int maxLength = Math.min(value.length - offset, length);
		final byte[] copy = new byte[maxLength];
		System.arraycopy(value, offset, copy, 0, maxLength);
		return copy;
	}

	@Override
	@NonNull
	public WriteRequest done(final @NonNull SuccessCallback callback) {
		this.successCallback = callback;
		return this;
	}

	@Override
	@NonNull
	public WriteRequest fail(final @NonNull FailCallback callback) {
		this.failCallback = callback;
		return this;
	}

	@Override
	@NonNull
	public WriteRequest with(final @NonNull DataSentCallback callback) {
		this.valueCallback = callback;
		return this;
	}

	/**
	 * Adds a splitter that will be used to cut given data into multiple packets.
	 * The splitter may modify each packet if necessary, i.e. add a flag indicating first packet,
	 * continuation or the last packet.
	 *
	 * @param splitter an implementation of a splitter
	 * @return the request
	 * @see #split()
	 */
	@NonNull
	public WriteRequest split(final @NonNull DataSplitter splitter) {
		this.dataSplitter = splitter;
		this.progressCallback = null;
		return this;
	}

	/**
	 * Adds a splitter that will be used to cut given data into multiple packets.
	 * The splitter may modify each packet if necessary, i.e. add a flag indicating first packet,
	 * continuation or the last packet.
	 *
	 * @param splitter an implementation of a splitter
	 * @param callback the progress callback that will be notified each time a packet was sent
	 * @return the request
	 * @see #split()
	 */
	@NonNull
	public WriteRequest split(final @NonNull DataSplitter splitter, final @NonNull WriteProgressCallback callback) {
		this.dataSplitter = splitter;
		this.progressCallback = callback;
		return this;
	}

	/**
	 * Adds a default MTU splitter that will be used to cut given data into at-most MTU-3
	 * bytes long packets.
	 *
	 * @return the request
	 */
	@NonNull
	public WriteRequest split() {
		this.dataSplitter = MTU_SPLITTER;
		this.progressCallback = null;
		return this;
	}

	/**
	 * Adds a default MTU splitter that will be used to cut given data into at-most MTU-3
	 * bytes long packets.
	 *
	 * @param callback the progress callback that will be notified each time a packet was sent
	 * @return the request
	 */
	@NonNull
	public WriteRequest split(final @NonNull WriteProgressCallback callback) {
		this.dataSplitter = MTU_SPLITTER;
		this.progressCallback = callback;
		return this;
	}

	byte[] getData(final int mtu) {
		if (dataSplitter == null || data == null) {
			complete = true;
			return data;
		}

		final byte[] chunk = dataSplitter.chunk(data, count, mtu - 3);
		if (chunk == null) // all data were sent
			complete = true;
		return chunk;
	}

	void notifyPacketSent(final @NonNull BluetoothDevice device, final byte[] data) {
		if (progressCallback != null)
			progressCallback.onPacketSent(device, data, count);
		count++;
		if (complete && valueCallback != null)
			valueCallback.onDataSent(device, new Data(WriteRequest.this.data));
	}

	boolean hasMore() {
		return !complete;
	}

	int getWriteType() {
		return writeType;
	}
}