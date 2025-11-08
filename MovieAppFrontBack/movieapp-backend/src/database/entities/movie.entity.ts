// entita database per memorizzare i film
// contiene solo dati del film (senza dati utente come rating personale)

import {
  Entity,
  Column,
  PrimaryColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

// decoratore entity per typeorm
// tabella movies nel database postgresql
// indici per ottimizzare ricerche frequenti
@Entity('movies')
@Index(['title', 'year'])
@Index(['tmdb_id'])
@Index(['source'])
@Index(['is_enriched'])
export class MovieEntity {
  // ===== identificatori =====
  
  // chiave primaria: id unico del film
  @PrimaryColumn({ type: 'varchar', length: 255 })
  id: string;

  // ===== dati base =====
  
  // titolo del film (obbligatorio)
  @Column({ length: 500 })
  title: string;

  // anno di uscita (opzionale)
  @Column({ nullable: true })
  year?: number;

  // sorgente dei dati: IMDB e LETTERBOXD
  @Column({ length: 50, default: 'UNKNOWN' })
  source: string;

  // ===== dati tmdb arricchiti =====
  
  // id ufficiale the movie database
  // usato per recuperare dati aggiuntivi e poster
  @Column({ nullable: true })
  tmdb_id?: number;

  // flag per indicare se il film e stato arricchito con dati tmdb
  // evita di richiamare api tmdb per film gia processati (cache)
  @Column({ default: false })
  is_enriched: boolean;

  // lista generi del film (azione, commedia, drammatico, etc)
  // array postgresql per supportare film con piu generi
  @Column('text', { array: true, default: '{}' })
  genres: string[];

  // regista principale del film
  @Column({ nullable: true, length: 255 })
  director?: string;

  // cast principale (attori)
  // array per supportare multipli attori
  @Column('text', { array: true, nullable: true })
  actors?: string[];

  // trama del film (descrizione estesa)
  @Column('text', { nullable: true })
  overview?: string;

  // tagline/slogan del film
  @Column({ nullable: true, length: 500 })
  tagline?: string;

  // durata in minuti
  @Column({ nullable: true })
  runtime?: number;

  // ===== poster e immagini =====
  
  // url poster principale
  @Column({ nullable: true, length: 500 })
  poster_url?: string;

  // url backdrop/sfondo per dettagli
  @Column({ nullable: true, length: 500 })
  backdrop_url?: string;

  // ===== rating e popolarita tmdb =====
  
  // rating medio tmdb (0.0 - 10.0)
  @Column('decimal', { precision: 3, scale: 1, nullable: true })
  tmdb_rating?: number;

  // numero totale voti su tmdb
  @Column({ nullable: true })
  vote_count?: number;

  // indice popolarita tmdb (algoritmo proprietario)
  @Column('decimal', { precision: 10, scale: 3, nullable: true })
  popularity?: number;

  // ===== dati produzione =====
  
  // budget produzione in dollari
  // bigint per supportare budget molto grandi
  @Column('bigint', { nullable: true })
  budget?: number;

  // incassi totali in dollari
  @Column('bigint', { nullable: true })
  revenue?: number;

  // stato produzione: released, post production, etc
  @Column({ nullable: true, length: 100 })
  status?: string;

  // studi produzione (warner bros, universal, etc)
  @Column('text', { array: true, default: '{}' })
  production_companies: string[];

  // paesi produzione (usa, uk, france, etc)
  @Column('text', { array: true, default: '{}' })
  production_countries: string[];

  // ===== lingue e titolo originale =====
  
  // lingua originale del film (codice iso: en, it, fr, etc)
  @Column({ nullable: true, length: 10 })
  original_language?: string;

  // titolo originale (se diverso dal titolo tradotto)
  @Column({ nullable: true, length: 500 })
  original_title?: string;

  // lingue parlate nel film
  @Column('text', { array: true, default: '{}' })
  spoken_languages: string[];

  // ===== metadata vari =====
  
  // flag contenuto adulto
  @Column({ default: false })
  adult: boolean;

  // sito web ufficiale del film
  @Column({ nullable: true, length: 500 })
  homepage?: string;

  // id imdb (formato: tt1234567)
  @Column({ nullable: true, length: 20 })
  imdb_id?: string;

  // parole chiave associate al film
  @Column('text', { array: true, default: '{}' })
  keywords: string[];

  // classificazione eta (pg-13, r, etc)
  @Column({ nullable: true, length: 20 })
  certification?: string;

  // url trailer youtube/vimeo
  @Column({ nullable: true, length: 500 })
  trailer_url?: string;

  // ===== timestamp automatici =====
  
  // data creazione record nel database
  @CreateDateColumn()
  created_at: Date;

  // data ultimo aggiornamento record
  @UpdateDateColumn()
  updated_at: Date;
}