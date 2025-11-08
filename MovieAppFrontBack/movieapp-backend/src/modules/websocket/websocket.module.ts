// modulo websocket per notifiche real-time
// gestisce progress enrichment, aggiornamenti live, notifiche push

import { Module } from '@nestjs/common';
import { WebsocketGateway } from './websocket.gateway';

@Module({
  imports: [],
  
  // websocket gateway gestisce connessioni socket.io
  providers: [WebsocketGateway],
  
  // esporta gateway per essere usato in movies module
  // movies service lo usa per inviare notifiche enrichment progress
  exports: [WebsocketGateway],
})
export class WebsocketModule {}