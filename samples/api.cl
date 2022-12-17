CONSTANT :: 5;

print :: fn(msg: string) {
  #intrinsic;
}

sleep :: fn(millis: int) {
  #intrinsic;
}

// SOCKETS

ServerSocket :: struct {fd: int,}
Socket :: struct {fd: int,}

open_server :: fn(port: int) ServerSocket {
  #intrinsic;
}
accept_client :: fn(server: ServerSocket) Socket {
  #intrinsic;
}
connect_server :: fn(host: string, port: int) Socket {
  #intrinsic;
}
send :: fn(socket: Socket, data: []i8) bool {
  #intrinsic;
}
recv :: fn(socket: Socket) []i8 {
  #intrinsic;
}
