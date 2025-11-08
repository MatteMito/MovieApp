// guard per protezione endpoint che richiedono autenticazione
// usa jwt strategy per validare token nel header authorization

import {
  Injectable,
  ExecutionContext,
  UnauthorizedException,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { Observable } from 'rxjs';

@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  /**
   * determina se richiesta puo accedere all'endpoint
   * chiama automaticamente jwt strategy per validare token
   */
  canActivate(
    context: ExecutionContext,
  ): boolean | Promise<boolean> | Observable<boolean> {
    // delega validazione a passport jwt strategy
    return super.canActivate(context);
  }

  /**
   * gestisce risultato validazione
   * chiamato dopo validate() della strategy
   */
  handleRequest(err: any, user: any, info: any) {
    // se errore o utente non valido, lancia unauthorized exception
    if (err || !user) {
      throw err || new UnauthorizedException('autenticazione richiesta');
    }
    
    // ritorna user per iniettarlo in request.user
    return user;
  }
}