// entita database per memorizzare gli utenti registrati
// gestisce autenticazione, profilo e stato account

import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

// decoratore entity per typeorm
// tabella users nel database postgresql
// indice unico su email per prevenire duplicati
@Entity('users')
@Index(['email'], { unique: true }) // garantisce unicita email
export class UserEntity {
  // id univoco utente generato automaticamente
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // email utente (obbligatoria e unica)
  @Column({ unique: true })
  email: string;

  // password hashata con bcrypt
  @Column()
  password: string;

  // username pubblico (opzionale)
  @Column({ nullable: true })
  username?: string;

  // url immagine profilo (opzionale)
  @Column({ nullable: true })
  avatar_url?: string;

  // flag account attivo
  // permette di disabilitare account senza eliminarli
  @Column({ default: true })
  is_active: boolean;

  // timestamp ultimo login
  // utile per analisi attivita utente
  @Column({ type: 'timestamp', nullable: true })
  last_login?: Date;

  // timestamp creazione account
  @CreateDateColumn()
  created_at: Date;

  // timestamp ultimo aggiornamento profilo
  @UpdateDateColumn()
  updated_at: Date;
}