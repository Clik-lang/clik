#load "api.cl";

main :: () {
  port :: 25577;
  server :: open_server(port);
  print("Server started on port ");
  id := 0;
  for {
    client :: accept_client(server);
    id = id + 1;
    print("Client connected ");
    spawn {
      print("Handling client ");
      backend :: connect_server("localhost", 25565);
      handle_client(id, client, backend);
    }
  }
}

handle_client :: (id: int, client: Socket, backend: Socket) {
  stop :~ false;
  forward :: (receiver: Socket, sender: Socket) {
    data := [25000]i8;
    for {
      Result :: struct {length: int, success: bool};
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

      select {
        -> send(sender, data, length);
        -> stop = $stop;
        {sleep(30000); stop = true;}
      }
      if stop break;
    }
    stop = true;
  }
  join {
    -> forward(client, backend);
    -> forward(backend, client);
  }
  print("Client disconnected ");
  close(client);
}
