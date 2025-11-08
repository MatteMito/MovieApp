// entita database per liste di film condivisibili
// supporta liste pubbliche/private, follower, target dates

import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';

// decoratore entity per typeorm
// tabella movie_lists nel database postgresql
@Entity('movie_lists')
export class MovieListEntity {
  // id univoco lista generato automaticamente
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // foreign key verso utente proprietario della lista
  // ogni lista appartiene a un utente
  @Column({ name: 'user_id', type: 'uuid' })
  user_id: string;

  // nome lista (es: "film da vedere a natale")
  // obbligatorio, max 200 caratteri
  @Column({ type: 'varchar', length: 200 })
  name: string;

  // descrizione opzionale della lista
  // testo libero per spiegare tema/scopo lista
  @Column({ type: 'text', nullable: true })
  description: string;

  // array di id film contenuti nella lista
  @Column({ name: 'movie_ids', type: 'text', array: true, default: '{}' })
  movie_ids: string[];

  // flag visibilita lista
  // true = visibile a tutti gli utenti
  // false = visibile solo al proprietario
  @Column({ name: 'is_public', type: 'boolean', default: false })
  is_public: boolean;

  // array di id utenti che seguono la lista
  // solo per liste pubbliche
  @Column({ name: 'follower_ids', type: 'text', array: true, default: '{}' })
  follower_ids: string[];

  // contatore numero follower
  @Column({ name: 'followers_count', type: 'int', default: 0 })
  followers_count: number;

  // data obiettivo opzionale
  @Column({ name: 'target_date', type: 'date', nullable: true })
  target_date: Date;

  // frequenza visione opzionale
  @Column({ type: 'varchar', length: 50, nullable: true })
  frequency: string;

  // timestamp creazione lista
  @CreateDateColumn({ name: 'created_at' })
  created_at: Date;

  // timestamp ultimo aggiornamento lista
  @UpdateDateColumn({ name: 'updated_at' })
  updated_at: Date;
}