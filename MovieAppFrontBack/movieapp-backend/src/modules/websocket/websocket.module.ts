// module websocket per notifiche real-time

import { Module } from '@nestjs/common';
import { WebsocketGateway } from './websocket.gateway';

@Module({
  providers: [WebsocketGateway], // gateway socket.io per eventi real-time
  exports: [WebsocketGateway], // esporta per usare in movies module (progress enrichment)
})
export class WebsocketModule {}