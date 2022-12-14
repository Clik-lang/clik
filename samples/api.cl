CONSTANT :: 5;

print :: (msg: string) {
  #intrinsic;
}

sleep :: (millis: int) {
  #intrinsic;
}

// SOCKETS

ServerSocket :: struct {fd: int,}
Socket :: struct {fd: int,}
RecvResult :: struct {length: int, success: bool}

open_server :: (port: int) ServerSocket {
  #intrinsic;
}
accept_client :: (server: ServerSocket) Socket {
  #intrinsic;
}
connect_server :: (host: string, port: int) Socket {
  #intrinsic;
}
send :: (socket: Socket, data: []i8, length: int) bool {
  #intrinsic;
}
recv :: (socket: Socket, data: []i8) RecvResult {
  #intrinsic;
}
close :: (socket: Socket) {
  #intrinsic;
}
