// strategia passport per validazione jwt token
// usata dai guard per proteggere endpoint autenticati

import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    private configService: ConfigService,
    private authService: AuthService,
  ) {
    // configura strategia jwt
    super({
      // estrae token dal header authorization come bearer token
      // formato: Authorization: Bearer <token>
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      
      // non ignorare scadenza token
      // se token scaduto, rifiuta richiesta
      ignoreExpiration: false,
      
      // secret key per verificare firma token
      // deve corrispondere a quella usata per firmare
      secretOrKey: configService.get<string>(
        'JWT_SECRET',
        'movieapp-secret-key',
      ),
    });
  }

  /**
   * metodo chiamato automaticamente da passport dopo validazione token
   * payload: dati decodificati dal token jwt
   * ritorna oggetto user che viene iniettato nel request
   */
  async validate(payload: any) {
    // verifica che utente esista ancora nel database
    // utente potrebbe essere stato eliminato dopo emissione token
    const user = await this.authService.getUserById(payload.sub);

    if (!user) {
      throw new UnauthorizedException('utente non valido');
    }

    // ritorna oggetto user semplificato
    // questo oggetto sara disponibile in request.user nei controller
    return {
      userId: payload.sub,
      email: payload.email,
      username: payload.username,
    };
  }
}