main :: () {
  port :: 25577;
  server :: open_server(port);
  print("Server started on port ", port);
  id := 0;
  for {
    client :: accept_client(server);
    id := id + 1;
    print("Client connected ", id);
    spawn {
      print("Handling client ", id);
      for {
        data :: recv(client, 1024);
        print("Client ", id, " sent: ", data);
      }
      close(client);
    }
  }
}
