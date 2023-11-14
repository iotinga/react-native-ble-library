# react-native-ble-library

This library aims to be a simple, yet with enough feature to be useful, library to connect to BLE devices.

It was made with the use case of providing a solid and reliable BLE implementation, built directly on the
native iOS/Android BLE interface, thus minimizing the possibility of errors and instability.

The feature that currently supports the library are:
- requesting BLE permissions
- scanning for BLE devices (with the possibility to set a scan filter as service UUIDs)
- connecting to a BLE device (only 1 connection at a time is supported)
- reading/writing BLE characteristics
- subscribing for BLE characteristics notifications
- demo manager to test your application in a simulator

It supports also the unique feature of repeated write/read. More on that later!

### Permission handling

The application requests the minimal permissions for it to work. Permissions are requested automatically
when calling the `init()` methods, and an appropriate error code is returned if the user didn't accepted
the required permissions.

#### iOS

On iOS it's necessary to add to the app `Info.plist` the key `NSBluetoothAlwaysUsageDescription` that provided
an usage description for the BLE, otherwise the application will crash when requesting the permissions.

To do so using Expo, add this key in `app.json` (and run `expo prebuild` if in a bare project):

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "NSBluetoothAlwaysUsageDescription": "This app needs Bluetooth to scan and connect to BLE devices"
      },
    },
  }
}
```

#### Android

In Android permissions are added automatically using the manifest merging feature, so no additional setup is needed.

For Android version >= 12 (API level 31) these permissions are required:
- BLUETOOTH_CONNECT
- BLUETOOTH_SCAN

Otherwise for older Android versions these permissions are required:
- BLUETOOTH
- BLUETOOTH_ADMIN
- ACCESS_FINE_LOCATION

You just have to ensure these permissions are not explicitly blocked in your application manifest (if you didn't do
it explicitly, they aren't).

On Android if the user had Bluetooth off it is also asked to turn it on automatically when calling the `init()` method.

### Repeated write/read

Often in devices, especially embedded devices, it's necessary to transfer more data than a characteristic may hold. For
example a typical use case is sending a firmware upgrade (OTA) to the device, or downloading a large file. For this reason
the library supports the repeated write/read operation. In this mode a characteristic is read/written multiple times and
only the final result sent to the application (although it's possible to set a subscription to get the operation progress).

The read/write is entirely managed in the native part, thus offloading the JS interface between the native module and the
JS runtime, that is often the bottleneck. This gives the operation a much higher stability and chance of success.

To give some numbers, I've tried other 2 BLE library and they didn't have this feature. Implementing it in JS was not stable
enough for my use case (sending a firmware upgrade of nearly 700kb to an embedded device), while with this library is stable
as a rock.

#### Repeated write

Simply call the `write()` function. If the length of the data you are writing is greater than the chunk size (default: 512 bytes)
then the write is split into multiple reads with this algorithm in pseudo-code:

```js
while (written < data.length) {
  chunk = next_chunk(data)
  send(chunk)
  wait_ack()
  progress(written, data.length)
  written += chunk.length
}
```

#### Repeated read

Simply call the `read()` function passing a `size` parameter. The read is repeated till:
- `size` bytes are received
- `size` bytes are not received by a char read returns a data of length 1 with the byte `0xff`
- an error occurs

See the following pseudo-code
```js
while (data.length < size) {
  chunk = read()
  if (chunk.length == 1 && chunk[0] == 0xff) {
    break
  }
  data += chunk
  progress(data.length, size)
}
return data
```

## Usage

### Installation

```sh
npm install @iotinga/react-native-ble-library
```

The library should link automatically both in plain React Native and Expo.

### Usage

Refer to the JSDoc of the BleManager interface to know how to use the module, and look
at the code in the `example/` directory!


