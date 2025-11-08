// strategia jwt per passport (validazione token nelle richieste protette)

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
    super({
      // estrae token dall'header Authorization: Bearer <token>
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      // rifiuta token scaduti
      ignoreExpiration: false,
      // chiave segreta per verificare firma token (deve coincidere con quella usata per firmare)
      secretOrKey: configService.get<string>(
        'JWT_SECRET',
        'movieapp-secret-key',
      ),
    });
  }

  // metodo chiamato automaticamente dopo verifica firma token
  // riceve payload decodificato e deve validare l'utente
  async validate(payload: any) {
    // verifica che l'utente esista ancora nel database
    const user = await this.authService.getUserById(payload.sub);

    if (!user) {
      throw new UnauthorizedException('utente non valido');
    }

    // ritorna oggetto utente che verrà iniettato in req.user
    return {
      userId: payload.sub,
      email: payload.email,
      username: payload.username,
    };
  }
}