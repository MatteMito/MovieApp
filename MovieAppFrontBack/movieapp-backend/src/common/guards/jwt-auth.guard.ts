// guard per protezione endpoint con jwt

import {
  Injectable,
  ExecutionContext,
  UnauthorizedException,
} from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { Observable } from 'rxjs';

@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  // verifica se la richiesta può essere processata
  canActivate(
    context: ExecutionContext,
  ): boolean | Promise<boolean> | Observable<boolean> {
    // delega la validazione alla strategia jwt configurata
    return super.canActivate(context);
  }

  // gestisce il risultato della validazione jwt
  handleRequest(err: any, user: any, info: any) {
    // se c'è un errore o l'utente non è valido, blocca la richiesta
    if (err || !user) {
      throw err || new UnauthorizedException('autenticazione richiesta');
    }
    // ritorna l'utente validato che verrà iniettato nel request object
    return user;
  }
}