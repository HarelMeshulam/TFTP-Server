package bgu.spl.net.impl.tftp;

import bgu.spl.net.impl.tftp.*;
import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {
        Server.threadPerClient(
                Integer.parseInt(args[0]), //Port
                () -> new TftpProtocol(), //Protocol factory
                TftpEncoderDecoder::new //Message encoder decoder factory
        ).serve();

    }
}
