// entity utente per autenticazione e profilo

import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('users')
@Index(['email'], { unique: true }) // indice unico su email per login rapido
export class UserEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string; // id univoco utente

  @Column({ unique: true })
  email: string; // email per login (unique constraint)

  @Column()
  password: string; // password hashata (bcrypt)

  @Column({ nullable: true })
  username?: string; // username opzionale per display

  @Column({ nullable: true })
  avatar_url?: string; // url immagine profilo

  @Column({ default: true })
  is_active: boolean; // flag per account attivo/disattivato

  @Column({ type: 'timestamp', nullable: true })
  last_login?: Date; // timestamp ultimo accesso

  @CreateDateColumn()
  created_at: Date; // data registrazione

  @UpdateDateColumn()
  updated_at: Date; // data ultimo aggiornamento profilo
}