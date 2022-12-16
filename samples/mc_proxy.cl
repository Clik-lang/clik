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
      // Handle client
      spawn {
        for {
          select {
            {data :: recv(client); send(backend, data);} {}
            stop = $stop; {break;}
            sleep(30000); {break;}
          }
        }
        stop = true;
      }
      // Handle backend
      spawn {
        for {
          select {
            {data :: recv(backend); send(client, data);} {}
            stop = $stop; {break;}
            sleep(30000); {break;}
          }
        }
        stop = true;
      }
      stop = $stop; // Wait for the fibers to stop
      print("Client disconnected ");
      close(client);
    }
  }
}
