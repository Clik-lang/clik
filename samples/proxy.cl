print :: (text: UTF8);
sleep :: (millis: I32);
open_server :: (text: I32) I32;
accept_client :: (fd: I32) I32;
connect_server :: (host: UTF8, port: I32) I32;
send :: (socket: I32, data: []i8, length: I32) bool;
recv :: (socket: I32, data: []i8) RecvResult;
close :: (socket: I32);

Client :: struct {
  id: int,
  client_socket: I32,
  backend_socket: I32,
}

handle_client :: (client: Client) {
  stop :~ false;
  forward :: (receiver: I32, sender: I32) {
    data := [2_000_000]i8;
    for {
      Result :: struct {length: int, success: bool};
      // Read socket
      length, success :Result= select {
        {
          length, success :: recv(receiver, data);
          {length, success};
        }
        {stop = $stop; {0, false};}
        {sleep(30000); {0, false};}
      }
      if !success break;
      // Write socket
      select {
        -> send(sender, data, length);
        -> stop = $stop;
        {sleep(30000); stop = true;}
      }
      if stop break;
    }
    stop = true;
  }
  // Run the two streams (client -> backend and backend -> client) in parallel
  spawn forward(client.client_socket, client.backend_socket);
  spawn forward(client.backend_socket, client.client_socket);
}

port :: I32.25577;
server_socket :: open_server(port);
print("Server started on port ");
id := 0;
for {
  client_socket :: accept_client(server_socket);
  id = id + 1;
  print("Client connected");
  spawn {
    print("Handling client");
    backend_socket :: connect_server("localhost", 25565);
    client :: Client {id, client_socket, backend_socket};
    handle_client(client);
    print("Client disconnected");
    close(client_socket);
    close(backend_socket);
  }
}
