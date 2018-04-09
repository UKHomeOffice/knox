/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, OnInit, ViewChild, ViewEncapsulation} from '@angular/core';
import { Topology } from './topology';
import {TopologyService} from "./topology.service";
import { ModalComponent } from 'ng2-bs3-modal/ng2-bs3-modal';
import { ViewChildren } from '@angular/core/src/metadata/di';

import 'brace/theme/monokai';
import 'brace/mode/xml';

@Component({
    selector: 'topology-detail',
    template: `
     <div class="panel panel-default">
        <div class="panel-heading">
            <h4 class="panel-title">{{title}} <span *ngIf="showEditOptions == false" style="padding-left: 15%;" class="text-danger text-center" > Ready Only (generated file) </span> <span class="label label-default pull-right">{{titleSuffix}}</span></h4>
         </div>
     <div *ngIf="topologyContent" class="panel-body">
      <ace-editor
        [(text)]="topologyContent" 
        [mode]="'xml'" 
        [options]="options" 
        [theme]="theme"
        style="min-height: 430px; width:100%; overflow: auto;" 
        (textChanged)="onChange($event)">
      </ace-editor>
       <div *ngIf="showEditOptions" class="panel-footer">
        <button id="duplicateTopology" (click)="duplicateModal.open('sm')" class="btn btn-default btn-sm" type="submit" >
            <span class="glyphicon glyphicon-duplicate" aria-hidden="true"></span>
        </button>
        <button id="deleteTopology" (click)="deleteConfirmModal.open('sm')" class="btn btn-default btn-sm" type="submit" >
            <span class="glyphicon glyphicon-trash" aria-hidden="true"></span>
        </button>
       <button id="saveTopology" (click)="saveTopology()" class="btn btn-default btn-sm pull-right" [disabled]="!changedTopology" type="submit" >
            <span class="glyphicon glyphicon-floppy-disk" aria-hidden="true"></span>
        </button>
       </div>
         
    </div>
    <modal (onClose)="createTopology()" #duplicateModal>

        <modal-header [show-close]="true">
            <h4 class="modal-title">Create a copy</h4>
        </modal-header>
        <modal-body>
            <div class="form-group">
                <label for="textbox">Name the new topology</label>
                <input autofocus type="text" class="form-control" required [(ngModel)]="newTopologyName" id="textbox">
            </div> 
        </modal-body>
        <modal-footer>
            <button type="button" class="btn btn-default btn-sm" data-dismiss="duplicateModal" (click)="duplicateModal.dismiss()">Cancel</button>
            <button type="button" class="btn btn-primary btn-sm" [disabled]="!newTopologyName" (click)="duplicateModal.close()">Ok</button>
        </modal-footer>
    </modal>
    <modal (onClose)="deleteTopology()" #deleteConfirmModal>
        <modal-header [show-close]="true">
            <h4 class="modal-title">Deleting Topology {{titleSuffix}}</h4>
        </modal-header>
        <modal-body>
            Are you sure you want to delete the topology?
        </modal-body>
        <modal-footer>
            <button type="button" class="btn btn-default btn-sm" data-dismiss="deleteConfirmModal" (click)="deleteConfirmModal.dismiss()">Cancel</button>
            <button type="button" class="btn btn-primary btn-sm" (click)="deleteConfirmModal.close()">Ok</button>
        </modal-footer>
    </modal>
   `
})
export class TopologyDetailComponent implements OnInit {

    title = 'Topology Detail';
    titleSuffix: string;
    topology: Topology;
    topologyContent: string;
    changedTopology: string;
    newTopologyName: string;
    readOnly: boolean;
    showEditOptions:boolean = true;
    theme: String = "monokai";
    options:any = {useWorker: false, printMargin: false};

    @ViewChild('duplicateModal')
    duplicateModal: ModalComponent;

    @ViewChild('deleteConfirmModal')
    deleteConfirmModal: ModalComponent;

    @ViewChild('editor') editor;

    constructor(private topologyService : TopologyService) {
    }

    ngOnInit(): void {
        this.topologyService.selectedTopology$.subscribe(value => this.populateContent(value));
    }

    setTitle(value : string) {
        this.titleSuffix = value;
    }

    onChange(code: any) {
        this.changedTopology = code;
    }

    saveTopology() {
        this.topologyService.saveTopology(this.topology.href, this.changedTopology)
        .then(value => this.topologyService.changedTopology(this.topology.name));
    }

    createTopology() {
        if (this.changedTopology) {
            this.topologyService.createTopology(this.newTopologyName, this.changedTopology)
            .then(value => this.topologyService.changedTopology(this.newTopologyName));
        } else {
            this.topologyService.createTopology(this.newTopologyName, this.topologyContent)
            .then(value => this.topologyService.changedTopology(this.newTopologyName));
        }
    }

    deleteTopology() {
        this.topologyService.deleteTopology(this.topology.href).then(value => this.topologyService.changedTopology(this.topology.name));
    }

    populateContent(topology: Topology) {
        this.topology = topology;
        this.setTitle(topology.name);
        if (this.topology) {
            if (this.topology.href) {
              this.topologyService.getTopology(this.topology.href).then( content => this.topologyContent = content).then(() => this.makeReadOnly(this.topologyContent, 'generated') );
            }
        }
    }

    /*
    * Parse the XML and depending on the  
    * provided tag value make the editor read only
    */
    makeReadOnly(text, tag) {
        var parser = new DOMParser();
        var parsed = parser.parseFromString(text,"text/xml");

        var tagValue = parsed.getElementsByTagName(tag);
        var result = tagValue[0].childNodes[0].nodeValue;
        
        if(result === 'true') {
            this.showEditOptions = false;
            this.options = {readOnly: true, useWorker: false, printMargin: false, highlightActiveLine: false, highlightGutterLine: false}; 
        } else {
            this.showEditOptions = true;
            this.options = {readOnly: false, useWorker: false, printMargin: false}; 
        }

    }


}