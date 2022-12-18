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
      stop :~ false;
      forward :: (receiver: Socket, sender: Socket) {
        for {
          select {
            {data :: recv(receiver); send(sender, data);} {}
            stop = $stop; {break;}
            sleep(30000); {break;}
          }
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
  }
}
