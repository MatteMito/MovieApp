import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';

// entity per le liste personalizzate di film create dagli utenti
@Entity('movie_lists')
export class MovieListEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'uuid' })
  user_id: string; // id dell'utente proprietario della lista

  @Column({ type: 'varchar', length: 200 })
  name: string; // nome della lista

  @Column({ type: 'text', nullable: true })
  description: string; // descrizione opzionale

  @Column({ name: 'movie_ids', type: 'text', array: true, default: '{}' })
  movie_ids: string[]; // array di id film nella lista

  @Column({ name: 'is_public', type: 'boolean', default: false })
  is_public: boolean; // se la lista è visibile pubblicamente

  @Column({ name: 'follower_ids', type: 'text', array: true, default: '{}' })
  follower_ids: string[]; // array di id utenti che seguono la lista

  @Column({ name: 'followers_count', type: 'int', default: 0 })
  followers_count: number; // contatore follower

  @Column({ name: 'target_date', type: 'date', nullable: true })
  target_date: Date; // data target per completare la lista

  @Column({ type: 'varchar', length: 50, nullable: true })
  frequency: string; // frequenza di aggiornamento (weekly, monthly, etc)

  @CreateDateColumn({ name: 'created_at' })
  created_at: Date; // data creazione

  @UpdateDateColumn({ name: 'updated_at' })
  updated_at: Date; // data ultimo aggiornamento (auto-aggiornato)
}