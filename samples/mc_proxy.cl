#load "api.cl";

State :: enum {
  HANDSHAKE, STATUS,
  LOGIN, PLAY
}
Client :: struct {
  id: int,
  client_socket: Socket,
  backend_socket: Socket,
  state: State,
}

main :: () {
  port :: 25577;
  server_socket :: open_server(port);
  print("Server started on port ");
  id := 0;
  for {
    client_socket :: accept_client(server_socket);
    id = id + 1;
    print("Client connected ");
    spawn {
      print("Handling client ");
      backend_socket :: connect_server("localhost", 25565);
      client :: Client {id, client_socket, backend_socket, State.HANDSHAKE};
      handle_client(client);
      print("Client disconnected ");
      close(client_socket);
      close(backend_socket);
    }
  }
}

handle_client :: (client: Client) {
  stop :~ false;
  forward :: (receiver: Socket, sender: Socket) {
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
      if !success {
        stop = true;
        break;
      }

      // TODO transform data

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
