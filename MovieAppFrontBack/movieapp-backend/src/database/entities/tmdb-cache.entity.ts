//entity cache tmdb per ottimizzazione chiamate api

import {
  Entity,
  Column,
  PrimaryColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

@Entity('tmdb_cache')
@Index(['tmdb_id'])
@Index(['expires_at'])
export class TmdbCacheEntity {
  @PrimaryColumn()
  cache_key: string;

  @Column()
  tmdb_id: number;

  @Column('jsonb')
  tmdb_data: any;

  //metadata per ricerca e statistiche
  @Column({ nullable: true })
  title?: string;

  @Column({ nullable: true })
  year?: number;

  @Column({ nullable: true })
  imdb_id?: string;

  //statistiche utilizzo
  @Column({ default: 0 })
  hit_count: number;

  @Column({ type: 'timestamp', nullable: true })
  last_accessed_at?: Date;

  @Column({ type: 'timestamp' })
  expires_at: Date;

  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;

  //metodi utility

  /**
   * verifica se cache scaduta
   */
  isExpired(): boolean {
    return new Date() > this.expires_at;
  }

  /**
   * incrementa contatore hit
   */
  incrementHit(): void {
    this.hit_count++;
    this.last_accessed_at = new Date();
  }

  /**
   * imposta scadenza cache (default 30 giorni)
   */
  setExpiry(days: number = 30): void {
    const expiryDate = new Date();
    expiryDate.setDate(expiryDate.getDate() + days);
    this.expires_at = expiryDate;
  }
}