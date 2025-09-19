import { NativeModule, requireNativeModule } from "expo";
import { BleErrorCode } from "./BleError";
import {
  BleDeviceInfo,
  ConnectionState,
  BleServiceInfo,
  BleServicesInfo,
} from "./types";

export type ReactNativeBleLibraryModuleEvents = {
  onError(data: { error: BleErrorCode; message: string }): void;
  onScanResult(data: { devices: BleDeviceInfo[] }): void;
  onCharValueChanged(data: {
    service: string;
    characteristic: string;
    value: string;
  }): void;
  onProgress(data: {
    service: string;
    characteristic: string;
    current: number;
    total: number;
    transactionId: string;
  }): void;
  onConnectionStateChanged(data: {
    state: ConnectionState;
    error: BleErrorCode | null;
    message: string;
    android?: { status: number };
    ios?: { code: number; description: string };
    services?: BleServiceInfo[];
  }): void;
};

declare class ReactNativeBleLibraryModule extends NativeModule<ReactNativeBleLibraryModuleEvents> {
  initModule(): Promise<void>;
  disposeModule(): Promise<void>;
  scanStart(serviceUuids?: string[]): Promise<void>;
  scanStop(): Promise<void>;
  connect(id: string, mtu: number): Promise<BleServicesInfo>;
  disconnect(): Promise<void>;
  write(
    transactionId: string,
    service: string,
    characteristic: string,
    value: string,
    chunkSize: number
  ): Promise<void>;
  read(
    transactionId: string,
    service: string,
    characteristic: string,
    size: number
  ): Promise<string>;
  subscribe(
    transactionId: string,
    service: string,
    characteristic: string
  ): Promise<void>;
  unsubscribe(
    transactionId: string,
    service: string,
    characteristic: string
  ): Promise<void>;
  readRSSI(transactionId: string): Promise<number>;
  cancel(transactionId: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReactNativeBleLibraryModule>(
  "ReactNativeBleLibrary"
);
