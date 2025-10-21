// ENTITY: Movie List
// Liste personalizzate di film create dagli utenti

import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { UserEntity } from './user.entity';

@Entity('movie_lists')
@Index(['user_id'])
@Index(['is_public'])
@Index(['target_date'])
export class MovieListEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // PROPRIETARIO LISTA
  
  @Column()
  user_id: string;

  @ManyToOne(() => UserEntity, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user: UserEntity;

  // DATI LISTA
  
  @Column({ length: 200 })
  name: string;

  @Column('text', { nullable: true })
  description?: string;

  @Column('simple-array')
  // Array di ID film
  movie_ids: string[]; 

  // PIANIFICAZIONE (opzionale)
  
  @Column({ type: 'timestamp', nullable: true })
  // Data obiettivo completamento
  target_date?: Date; 

  @Column({ length: 50, nullable: true })
// Frequenza visione (es: "1 al mese")
  frequency?: string;

  // FUNZIONALITÀ SOCIAL
  
  @Column({ default: false })
  // Lista visibile pubblicamente
  is_public: boolean; 

  @Column({ default: 0 })
  // Numero followers
  followers_count: number; 

  @Column('simple-array', { default: '' })
  // Array ID utenti followers
  follower_ids: string[]; 

  // TIMESTAMP AUTOMATICI
  
  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;
}