#load "api.cl";

main :: fn() {
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
      forward :: spawn(receiver: Socket, sender: Socket) {
        for {
          select {
            {data :: recv(receiver); send(sender, data);} {}
            stop = $stop; {break;}
            sleep(30000); {break;}
          }
        }
        stop = true;
      }
      forward(client, backend);
      forward(backend, client);
      stop = $stop; // Wait for the fibers to stop
      print("Client disconnected ");
      close(client);
    }
  }
}
